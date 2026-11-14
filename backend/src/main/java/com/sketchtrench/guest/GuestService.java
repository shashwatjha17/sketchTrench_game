package com.sketchtrench.guest;

import com.sketchtrench.exception.ApiException;
import com.sketchtrench.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The whole guest flow in one place: sessions, rooms, and the live game engine (rounds,
 * word picking, countdown, scoring). Pure in-memory — no repositories, no persistence.
 * Broadcasting goes out over the STOMP topics the frontend subscribes to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestService {

    private static final long DISCONNECT_TTL_SECONDS = 60;
    private static final long WORD_PICK_TIMEOUT_SECONDS = 20;
    private static final long ROUND_GAP_SECONDS = 4;
    private static final int GUESS_POINTS = 100;
    private static final int DRAWER_POINTS_PER_GUESSER = 25;
    private static final int MAX_STORED_DRAW_EVENTS = 3000;
    private static final int MAX_BOTS = 5;
    private static final String[] BOT_NAMES = {"Pixel", "Doodle", "Scribble", "Crayon", "Inky", "Smudge", "Chalky", "Squiggle"};
    private static final String[] BOT_COLORS = {"#ef4444", "#f97316", "#22c55e", "#3b82f6", "#a855f7", "#ec4899"};
    private static final String[] BOT_EXPRESSIONS = {"happy", "surprised", "wink"};
    private static final String[] BOT_WIGS = {"none", "curly", "bob", "spiky"};

    private final PlayerSessionManager players;
    private final RoomManager rooms;
    private final WebSocketSessionManager wsSessions;
    // @Lazy: GuestService is reached via the WS interceptor during broker init, so the
    // template must not force eager creation (avoids a circular bean dependency).
    private final @Lazy SimpMessagingTemplate messaging;
    private final TaskScheduler scheduler;

    // ---- sessions ----

    public GuestPlayer createSession(GuestDto.SessionRequest req) {
        if (req.nickname() == null || req.nickname().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NICKNAME_REQUIRED", "Choose a nickname");
        }
        return players.create(req);
    }

    public Map<String, Object> reconnect(GuestDto.ReconnectRequest req) {
        GuestPlayer player = players.byReconnectToken(req.reconnectToken());
        if (player == null || !player.playerId.equals(req.playerId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "This session is no longer valid");
        }
        GameRoom room = player.roomId == null ? null : rooms.get(player.roomId);
        return Map.of("player", toPlayerInfo(player),
                "roomId", room == null ? "" : room.roomId);
    }

    public void touch(GuestPlayer player) {
        player.isConnected = true;
    }

    public GuestPlayer requirePlayer(String playerId) {
        return players.get(playerId);
    }

    // ---- room listing / joining ----

    public List<GuestDto.RoomInfo> listPublicRooms() {
        return rooms.publicWaiting().stream().map(this::toRoomInfo).toList();
    }

    public GuestDto.RoomInfo getRoom(String roomId) {
        return toRoomInfo(requireRoom(roomId));
    }

    public GuestDto.RoomInfo createRoom(GuestPlayer host, GuestDto.CreateRoomRequest req) {
        if (host.roomId != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_ROOM", "Leave your current room first");
        }
        GameRoom room = rooms.create(req, host);
        room.players.put(host.playerId, host);
        room.hostId = host.playerId;
        host.roomId = room.roomId;
        host.isHost = true;
        broadcastRoom(room);
        return toRoomInfo(room);
    }

    public GuestDto.RoomInfo joinRoom(GuestPlayer player, String roomId) {
        if (player.roomId != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_ROOM", "Leave your current room first");
        }
        GameRoom room = requireRoom(roomId);
        if (room.banned.contains(player.playerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KICKED", "You were removed from this room");
        }
        if ("PLAYING".equals(room.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "GAME_IN_PROGRESS", "The game already started");
        }
        if (room.playerCount() >= room.maxPlayers) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_FULL", "Room is full");
        }
        room.players.put(player.playerId, player);
        player.roomId = room.roomId;
        broadcastRoom(room);
        return toRoomInfo(room);
    }

    public void leaveRoom(GuestPlayer player) {
        if (player.roomId == null) return;
        GameRoom room = rooms.get(player.roomId);
        player.roomId = null;
        player.isHost = false;
        if (room == null) return;
        boolean wasDrawer = player.playerId.equals(room.drawerId);
        room.players.remove(player.playerId);
        room.kickVotes.remove(player.playerId);
        room.kickVotes.values().forEach(v -> v.remove(player.playerId));

        // bots alone don't keep a room alive
        if (room.humanPlayers().isEmpty()) {
            cancelRoundTasks(room);
            rooms.remove(room.roomId);
            return;
        }
        if (player.playerId.equals(room.hostId)) {
            GuestPlayer next = room.humanPlayers().get(0);
            room.hostId = next.playerId;
            next.isHost = true;
        }
        broadcastRoom(room);
        // the drawer walked out mid-round: nobody can draw, so close the round now
        if (wasDrawer && "PLAYING".equals(room.status)) {
            endRound(room.roomId);
        }
    }

    // ---- bots ----

    /** Host adds a bot. Bots guess (with some chatter and near-misses) but never draw. */
    public GuestDto.RoomInfo addBot(GuestPlayer host, String roomId) {
        GameRoom room = requireRoom(roomId);
        requireHost(host, room, "Only the host can add bots");
        if (!"WAITING".equals(room.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "GAME_IN_PROGRESS", "Add bots before the game starts");
        }
        if (room.playerCount() >= room.maxPlayers) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_FULL", "Room is full");
        }
        List<String> taken = room.players.values().stream().map(p -> p.nickname).toList();
        long botCount = room.players.values().stream().filter(p -> p.isBot).count();
        if (botCount >= MAX_BOTS) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_BOTS", "That's enough bots for one room");
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String name = java.util.Arrays.stream(BOT_NAMES)
                .map(n -> n + " (bot)")
                .filter(n -> !taken.contains(n))
                .findFirst()
                .orElse("Bot " + (botCount + 1));
        GuestPlayer bot = new GuestPlayer(name,
                BOT_COLORS[rnd.nextInt(BOT_COLORS.length)],
                BOT_EXPRESSIONS[rnd.nextInt(BOT_EXPRESSIONS.length)],
                rnd.nextInt(4) == 0,
                BOT_WIGS[rnd.nextInt(BOT_WIGS.length)],
                "en");
        bot.isBot = true;
        bot.isConnected = true;
        bot.roomId = room.roomId;
        room.players.put(bot.playerId, bot);
        broadcastRoom(room);
        return toRoomInfo(room);
    }

    public GuestDto.RoomInfo removeBot(GuestPlayer host, String roomId, String botId) {
        GameRoom room = requireRoom(roomId);
        requireHost(host, room, "Only the host can remove bots");
        GuestPlayer bot = room.players.get(botId);
        if (bot == null || !bot.isBot) {
            throw new NotFoundException("Bot", botId);
        }
        room.players.remove(botId);
        broadcastRoom(room);
        return toRoomInfo(room);
    }

    /**
     * Schedules each bot's behaviour for the round just started: a wrong guess or two (sometimes
     * a near-miss), then — most of the time — the right answer somewhere between 25% and 90%
     * of the drawing time. Tasks re-check the round so a stale task never acts on a new one.
     */
    private void scheduleBotGuesses(GameRoom room) {
        String word = room.word;
        int round = room.roundIndex;
        long total = room.drawingTimeSec;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (GuestPlayer bot : room.orderedPlayers()) {
            if (!bot.isBot) continue;
            int wrong = rnd.nextInt(3);
            for (int i = 0; i < wrong; i++) {
                String text = rnd.nextInt(3) == 0 && word.length() >= 4
                        ? nearMiss(word, rnd)
                        : WordPool.pick(4).stream().filter(w -> !w.equalsIgnoreCase(word)).findFirst().orElse("hmm");
                long at = (long) (total * (0.1 + rnd.nextDouble() * 0.5));
                botTask(room, round, word, at, () -> handleMessage(bot, room.roomId, text));
            }
            if (rnd.nextInt(100) < 85) {
                long at = (long) (total * (0.25 + rnd.nextDouble() * 0.65));
                botTask(room, round, word, at, () -> submitGuess(bot.playerId, room.roomId, word));
            }
        }
    }

    private void botTask(GameRoom room, int round, String word, long delaySec, Runnable action) {
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            if (room.roundIndex != round || !word.equals(room.word) || !"PLAYING".equals(room.status)) return;
            try {
                action.run();
            } catch (RuntimeException e) {
                log.debug("bot action skipped: {}", e.getMessage());
            }
        }, Instant.now().plusSeconds(Math.max(2, delaySec)));
        synchronized (room.botTasks) {
            room.botTasks.add(f);
        }
    }

    private static String nearMiss(String word, ThreadLocalRandom rnd) {
        int i = rnd.nextInt(word.length());
        return word.substring(0, i) + word.substring(i + 1); // drop one letter
    }

    // ---- moderation ----

    /**
     * Vote to kick {@code targetId}. The host's vote kicks immediately; otherwise a strict
     * majority of the other human players is needed. Kicked players can't rejoin the room.
     */
    public void voteKick(GuestPlayer voter, String roomId, String targetId) {
        GameRoom room = requireRoom(roomId);
        if (!room.players.containsKey(voter.playerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_IN_ROOM", "You're not in this room");
        }
        GuestPlayer target = room.players.get(targetId);
        if (target == null) {
            throw new NotFoundException("Player", targetId);
        }
        if (target.isBot) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IS_BOT", "Bots are removed by the host, not kicked");
        }
        if (target.playerId.equals(voter.playerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_KICK", "You can't kick yourself");
        }
        boolean byHost = voter.playerId.equals(room.hostId);
        Set<String> votes = room.kickVotes.computeIfAbsent(targetId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (!votes.add(voter.playerId) && !byHost) return; // already voted
        long electorate = room.humanPlayers().stream().filter(p -> !p.playerId.equals(targetId)).count();
        long needed = electorate / 2 + 1;
        if (byHost || votes.size() >= needed) {
            kick(room, target);
        } else {
            system(room, voter.nickname + " voted to kick " + target.nickname + " (" + votes.size() + "/" + needed + ")");
        }
    }

    private void kick(GameRoom room, GuestPlayer target) {
        room.kickVotes.remove(target.playerId);
        room.banned.add(target.playerId);
        messaging.convertAndSendToUser(target.playerId, "/queue/kicked", new GuestDto.Kicked(room.roomId));
        system(room, target.nickname + " was kicked from the room");
        leaveRoom(target);
    }

    private void requireHost(GuestPlayer player, GameRoom room, String message) {
        if (!player.playerId.equals(room.hostId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOST_ONLY", message);
        }
    }

    private void system(GameRoom room, String text) {
        broadcastToRoom(room.roomId, new GuestDto.SystemMessage(text));
    }

    public void addCustomWords(GuestPlayer host, String roomId, List<String> words) {
        GameRoom room = requireRoom(roomId);
        if (!host.playerId.equals(room.hostId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOST_ONLY", "Only the host can add words");
        }
        if (!room.customWordsEnabled) {
            throw new ApiException(HttpStatus.CONFLICT, "CUSTOM_WORDS_DISABLED", "Custom words are off for this room");
        }
        if (words == null) return;
        for (String w : words) {
            String clean = w.trim();
            if (!clean.isEmpty() && clean.length() <= 64) {
                room.customWords.add(clean);
            }
        }
        broadcastRoom(room);
    }

    public void removeCustomWord(GuestPlayer host, String roomId, String word) {
        GameRoom room = requireRoom(roomId);
        if (!host.playerId.equals(room.hostId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOST_ONLY", "Only the host can remove words");
        }
        room.customWords.remove(word);
        broadcastRoom(room);
    }

    // ---- game flow ----

    public void startGame(GuestPlayer player, String roomId) {
        GameRoom room = requireRoom(roomId);
        if (!player.playerId.equals(room.hostId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOST_ONLY", "Only the host can start the game");
        }
        if (room.playerCount() < 2) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS", "Need at least 2 players");
        }
        if (room.customWordsEnabled && room.customWords.size() < 3) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_CUSTOM_WORDS", "Add at least 3 custom words first");
        }
        if ("PLAYING".equals(room.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "GAME_IN_PROGRESS", "The game already started");
        }
        room.status = "PLAYING";
        room.roundIndex = 0;
        room.startedAt = Instant.now();
        room.orderedPlayers().forEach(p -> {
            p.score = 0;
            p.isDrawing = false;
        });
        beginRound(room);
        broadcastRoom(room);
    }

    private void beginRound(GameRoom room) {
        List<GuestPlayer> ordered = room.orderedPlayers();
        List<GuestPlayer> humans = room.humanPlayers(); // bots guess but never draw
        if (humans.isEmpty()) return;
        GuestPlayer drawer = humans.get(room.roundIndex % humans.size());
        room.drawerId = drawer.playerId;
        room.guessed = new ArrayList<>();
        room.strokes = new ArrayList<>();
        ordered.forEach(p -> p.isDrawing = p.playerId.equals(drawer.playerId));

        List<String> texts = room.customWordsEnabled && room.customWords.size() >= 3
                ? new ArrayList<>(room.customWords)
                : WordPool.pick(3);
        java.util.Collections.shuffle(texts);
        List<GuestDto.WordOption> options = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            options.add(new GuestDto.WordOption(String.valueOf(i), texts.get(i)));
        }
        room.wordOptions = options;
        room.word = null;

        messaging.convertAndSendToUser(drawer.playerId, "/queue/word-options",
                new GuestDto.WordOptions(room.roundIndex + 1, options));
        broadcastToRoom(room.roomId, new GuestDto.RoundStarting(
                room.roundIndex + 1, drawer.playerId, drawer.nickname, options.size()));

        // drawer has WORD_PICK_TIMEOUT to choose; otherwise auto-pick the first
        int round = room.roundIndex;
        scheduler.schedule(() -> {
            if (room.roundIndex == round && drawer.playerId.equals(room.drawerId)
                    && room.word == null && !room.wordOptions.isEmpty()) {
                selectWord(drawer.playerId, room.roomId, room.wordOptions.get(0).id());
            }
        }, Instant.now().plusSeconds(WORD_PICK_TIMEOUT_SECONDS));
    }

    public void selectWord(String playerId, String roomId, String wordId) {
        GameRoom room = requireRoom(roomId);
        if (!playerId.equals(room.drawerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DRAWER_ONLY", "Only the drawer picks the word");
        }
        room.word = room.wordOptions.stream()
                .filter(o -> o.id().equals(wordId))
                .findFirst()
                .map(GuestDto.WordOption::text)
                .orElse(room.wordOptions.isEmpty() ? null : room.wordOptions.get(0).text());
        if (room.word == null) return;
        GuestPlayer drawer = room.players.get(room.drawerId);
        if (drawer == null) return; // drawer left while picking

        messaging.convertAndSendToUser(room.drawerId, "/queue/word", new GuestDto.SecretWord(room.word));
        broadcastToRoom(room.roomId, new GuestDto.RoundStarted(
                room.roundIndex + 1, room.drawerId, drawer.nickname, room.drawingTimeSec));

        room.roundEndsAt = Instant.now().plusSeconds(room.drawingTimeSec);
        scheduleTick(room);
        room.endRoundTask = scheduler.schedule(() -> endRound(room.roomId), room.roundEndsAt);
        scheduleBotGuesses(room);
    }

    private void scheduleTick(GameRoom room) {
        room.tickerTask = scheduler.scheduleAtFixedRate(() -> {
            long left = room.remainingSeconds();
            broadcastToRoom(room.roomId, new GuestDto.TimerUpdate((int) left));
            if (left <= 0) {
                room.tickerTask.cancel(false);
            }
        }, Duration.ofSeconds(1));
    }

    /** Guess attempt from a non-drawer. Returns the CorrectGuess broadcast, or null if it was just chat. */
    public GuestDto.CorrectGuess submitGuess(String playerId, String roomId, String text) {
        GameRoom room = requireRoom(roomId);
        String clean = normalize(text);
        if (room.word == null || playerId.equals(room.drawerId) || room.guessed.contains(playerId)) {
            return null;
        }
        if (!clean.equals(normalize(room.word))) {
            return null;
        }
        room.guessed.add(playerId);
        GuestPlayer guesser = room.players.get(playerId);
        int points = GUESS_POINTS + (int) (2 * room.remainingSeconds());
        guesser.score += points;
        int remainingGuessers = Math.max(0, room.playerCount() - 1 - room.guessed.size());
        GuestDto.CorrectGuess guess = new GuestDto.CorrectGuess(playerId, guesser.nickname, points, remainingGuessers);
        broadcastToRoom(room.roomId, guess);
        if (remainingGuessers == 0) {
            scheduler.schedule(() -> endRound(room.roomId), Instant.now().plusSeconds(ROUND_GAP_SECONDS));
        }
        return guess;
    }

    /**
     * Everything typed in the game chat lands here: a correct guess scores (and is never shown);
     * the drawer or anyone who already guessed can't leak the answer; a near-miss gets a private
     * "close!" hint; everything else is ordinary chat.
     */
    public void handleMessage(GuestPlayer player, String roomId, String text) {
        GameRoom room = rooms.get(roomId);
        if (room == null || text == null || text.isBlank() || !room.players.containsKey(player.playerId)) return;
        String trimmed = text.trim();
        if (trimmed.length() > 200) trimmed = trimmed.substring(0, 200);
        if (submitGuess(player.playerId, roomId, trimmed) != null) return;

        String word = room.word;
        if (word != null) {
            String clean = normalize(trimmed);
            String answer = normalize(word);
            boolean knowsWord = player.playerId.equals(room.drawerId) || room.guessed.contains(player.playerId);
            if (knowsWord && clean.contains(answer)) {
                messaging.convertAndSendToUser(player.playerId, "/queue/hint",
                        new GuestDto.SystemMessage("Shh — you can't say the word in chat!"));
                return;
            }
            if (!knowsWord && isClose(clean, answer)) {
                messaging.convertAndSendToUser(player.playerId, "/queue/hint", new GuestDto.CloseGuess(trimmed));
            }
        }
        chat(player, roomId, trimmed);
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** One typo away (two for long words) counts as close. */
    static boolean isClose(String guess, String answer) {
        if (guess.isEmpty() || answer.length() < 3) return false;
        int allowed = answer.length() >= 8 ? 2 : 1;
        if (Math.abs(guess.length() - answer.length()) > allowed) return false;
        int d = levenshtein(guess, answer);
        return d > 0 && d <= allowed;
    }

    /** Edit distance where swapping two neighbouring letters counts as one typo (optimal string alignment). */
    static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    public void chat(GuestPlayer player, String roomId, String text) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        broadcastToRoom(room.roomId, new GuestDto.ChatMessage(
                player.playerId, player.nickname, text, Instant.now()));
    }

    public void typing(GuestPlayer player, String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        broadcastToRoom(room.roomId, new GuestDto.TypingIndicator(player.playerId, player.nickname));
    }

    public void forwardStroke(GuestPlayer player, String roomId, Map<String, Object> stroke) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !player.playerId.equals(room.drawerId)) return;
        Object type = stroke.get("type");
        // keep the full event log (paths, fills, clears) so a late joiner's replay matches;
        // "undo" pops the last event for everyone
        synchronized (room) {
            if ("undo".equals(type)) {
                if (room.strokes.isEmpty()) return;
                room.strokes.remove(room.strokes.size() - 1);
            } else if (("path".equals(type) || "fill".equals(type) || "clear".equals(type))
                    && room.strokes.size() < MAX_STORED_DRAW_EVENTS) {
                room.strokes.add(stroke);
            } else if (!"path".equals(type) && !"fill".equals(type) && !"clear".equals(type)) {
                return; // unknown event type
            }
        }
        messaging.convertAndSend("/topic/drawing/" + roomId, stroke);
    }

    private void endRound(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !"PLAYING".equals(room.status)) return;
        cancelRoundTasks(room);

        GuestPlayer drawer = room.players.get(room.drawerId);
        int drawerBonus = DRAWER_POINTS_PER_GUESSER * room.guessed.size();
        if (drawer != null) drawer.score += drawerBonus;

        Map<String, Integer> scores = scores(room);
        broadcastToRoom(room.roomId, new GuestDto.RoundEnded(
                room.roundIndex + 1, room.word, room.drawerId, drawerBonus, scores));
        room.word = null;
        room.wordOptions = List.of();
        room.roundIndex++;

        if (room.roundIndex >= room.totalRounds || room.orderedPlayers().size() < 2) {
            endGame(room);
        } else {
            scheduler.schedule(() -> beginRound(room), Instant.now().plusSeconds(ROUND_GAP_SECONDS));
        }
    }

    private void endGame(GameRoom room) {
        room.status = "FINISHED";
        Map<String, Integer> scores = scores(room);
        GuestPlayer winner = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> room.players.get(e.getKey()))
                .orElse(null);
        broadcastToRoom(room.roomId, new GuestDto.GameEnded(
                winner == null ? null : winner.playerId,
                winner == null ? null : winner.nickname,
                scores));
        broadcastRoom(room);
    }

    // ---- presence / cleanup ----

    /** A socket for this player finished its STOMP handshake: mark present and tell the room. */
    public void onConnect(String playerId) {
        GuestPlayer player = players.get(playerId);
        if (player == null) return;
        player.isConnected = true;
        broadcastPresence(player);
    }

    /** A WebSocket session died. Marks the player away (if it was their last socket) and reaps them after TTL. */
    public void onDisconnect(String sessionId) {
        String playerId = wsSessions.unbind(sessionId);
        if (playerId == null) return;
        GuestPlayer player = players.get(playerId);
        if (player == null) return;
        // another tab / a newer reconnect is still live — the player hasn't gone anywhere
        if (wsSessions.isConnected(playerId)) return;
        player.isConnected = false;
        broadcastPresence(player);
        scheduler.schedule(() -> reapIfStillAway(player), Instant.now().plusSeconds(DISCONNECT_TTL_SECONDS));
    }

    private void reapIfStillAway(GuestPlayer player) {
        if (player.isConnected || wsSessions.isConnected(player.playerId)) return;
        players.remove(player);
        leaveRoom(player);
    }

    private void broadcastPresence(GuestPlayer player) {
        GameRoom room = player.roomId == null ? null : rooms.get(player.roomId);
        if (room != null) broadcastRoom(room);
    }

    private void cancelRoundTasks(GameRoom room) {
        if (room.tickerTask != null) room.tickerTask.cancel(true);
        if (room.endRoundTask != null) room.endRoundTask.cancel(true);
        room.tickerTask = null;
        room.endRoundTask = null;
        synchronized (room.botTasks) {
            room.botTasks.forEach(f -> f.cancel(false));
            room.botTasks.clear();
        }
    }

    // ---- snapshots / broadcasting ----

    public GameRoom requireRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new NotFoundException("Room", roomId);
        }
        return room;
    }

    public GuestDto.PlayerInfo toPlayerInfo(GuestPlayer p) {
        return new GuestDto.PlayerInfo(p.playerId, p.nickname, p.avatarColor, p.avatarExpression,
                p.avatarSunglasses, p.avatarWig, p.language, p.score, p.isHost, p.isDrawing, p.isConnected, p.isBot);
    }

    public GuestDto.RoomInfo toRoomInfo(GameRoom room) {
        return new GuestDto.RoomInfo(room.roomId, room.name, room.isPrivate, room.status, room.hostId,
                room.maxPlayers, room.totalRounds, room.drawingTimeSec, room.customWordsEnabled,
                room.roundIndex, room.playerCount(), new ArrayList<>(room.customWords),
                room.orderedPlayers().stream().map(this::toPlayerInfo).toList());
    }

    public GuestDto.GameState toGameState(GameRoom room, GuestPlayer viewer) {
        boolean drawer = viewer.playerId.equals(room.drawerId);
        return new GuestDto.GameState(room.roundIndex + 1, room.drawerId,
                room.drawerId == null || room.players.get(room.drawerId) == null
                        ? null : room.players.get(room.drawerId).nickname,
                room.drawingTimeSec, (int) room.remainingSeconds(), scores(room),
                drawer ? room.word : null, copyStrokes(room),
                "PLAYING".equals(room.status));
    }

    private List<Object> copyStrokes(GameRoom room) {
        synchronized (room) {
            return new ArrayList<>(room.strokes);
        }
    }

    private Map<String, Integer> scores(GameRoom room) {
        Map<String, Integer> out = new LinkedHashMap<>();
        room.orderedPlayers().forEach(p -> out.put(p.playerId, p.score));
        return out;
    }

    private void broadcastRoom(GameRoom room) {
        messaging.convertAndSend("/topic/room/" + room.roomId, toRoomInfo(room));
    }

    private void broadcastToRoom(String roomId, Object payload) {
        messaging.convertAndSend("/topic/game/" + roomId, payload);
    }
}
