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
import java.util.concurrent.ScheduledFuture;

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

    private final PlayerSessionManager players;
    private final RoomManager rooms;
    private final WebSocketSessionManager wsSessions;
    private final WordPool wordPool;
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
        if (room != null) {
            room.players.remove(player.playerId);
            if (room.hostId.equals(player.playerId)) {
                room.orderedPlayers().stream().findFirst()
                        .ifPresent(next -> { room.hostId = next.playerId; next.isHost = true; });
            }
            broadcastRoom(room);
            if (room.playerCount() == 0) {
                cancelRoundTasks(room);
                rooms.remove(room.roomId);
            }
        }
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
        if (ordered.isEmpty()) return;
        GuestPlayer drawer = ordered.get(room.roundIndex % ordered.size());
        room.drawerId = drawer.playerId;
        room.guessed = new ArrayList<>();
        room.strokes = new ArrayList<>();
        ordered.forEach(p -> p.isDrawing = p.playerId.equals(drawer.playerId));

        List<String> texts = room.customWordsEnabled && room.customWords.size() >= 3
                ? new ArrayList<>(room.customWords)
                : wordPool.pick(3);
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
        scheduler.schedule(() -> {
            if (room.word == null && !room.wordOptions.isEmpty()) {
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

        messaging.convertAndSendToUser(room.drawerId, "/queue/word", new GuestDto.SecretWord(room.word));
        broadcastToRoom(room.roomId, new GuestDto.RoundStarted(
                room.roundIndex + 1, room.drawerId, room.players.get(room.drawerId).nickname, room.drawingTimeSec));

        room.roundEndsAt = Instant.now().plusSeconds(room.drawingTimeSec);
        scheduleTick(room);
        room.endRoundTask = scheduler.schedule(() -> endRound(room.roomId), room.roundEndsAt);
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
        String clean = text == null ? "" : text.trim().toLowerCase();
        if (room.word == null || playerId.equals(room.drawerId) || room.guessed.contains(playerId)) {
            return null;
        }
        if (!clean.equals(room.word.toLowerCase())) {
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
        if ("path".equals(stroke.get("type")) && room.strokes.size() < 2000) {
            room.strokes.add(stroke);
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

    /** Called when a WebSocket session dies. Marks the player away and reaps them after TTL. */
    public void onDisconnect(String sessionId) {
        String playerId = wsSessions.playerForSession(sessionId);
        if (playerId == null) return;
        wsSessions.unbind(sessionId);
        GuestPlayer player = players.get(playerId);
        if (player == null) return;
        player.isConnected = false;
        scheduler.schedule(() -> reapIfStillAway(player), Instant.now().plusSeconds(DISCONNECT_TTL_SECONDS));
    }

    private void reapIfStillAway(GuestPlayer player) {
        if (player.isConnected) return;
        players.remove(player);
        leaveRoom(player);
    }

    private void cancelRoundTasks(GameRoom room) {
        if (room.tickerTask != null) room.tickerTask.cancel(true);
        if (room.endRoundTask != null) room.endRoundTask.cancel(true);
        room.tickerTask = null;
        room.endRoundTask = null;
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
                p.avatarSunglasses, p.avatarWig, p.language, p.score, p.isHost, p.isDrawing, p.isConnected);
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
                room.drawerId == null ? null : room.players.get(room.drawerId).nickname,
                room.drawingTimeSec, (int) room.remainingSeconds(), scores(room),
                drawer ? room.word : null, new ArrayList<>(room.strokes),
                "PLAYING".equals(room.status));
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
