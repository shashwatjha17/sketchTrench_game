package com.sketchtrench.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sketchtrench.common.websocket.RoomPublisher;
import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.game.dto.GameDto;
import com.sketchtrench.game.entity.Drawing;
import com.sketchtrench.game.entity.Guess;
import com.sketchtrench.game.entity.Round;
import com.sketchtrench.game.entity.RoomCustomWord;
import com.sketchtrench.game.entity.Score;
import com.sketchtrench.game.entity.Word;
import com.sketchtrench.game.entity.WordCategory;
import com.sketchtrench.game.model.GameSession;
import com.sketchtrench.game.repository.DrawingRepository;
import com.sketchtrench.game.repository.GuessRepository;
import com.sketchtrench.game.repository.RoundRepository;
import com.sketchtrench.game.repository.RoomCustomWordRepository;
import com.sketchtrench.game.repository.ScoreRepository;
import com.sketchtrench.game.repository.WordCategoryRepository;
import com.sketchtrench.game.repository.WordRepository;
import com.sketchtrench.progress.ProgressTracker;
import com.sketchtrench.room.entity.Room;
import com.sketchtrench.room.entity.RoomMember;
import com.sketchtrench.room.repository.RoomMemberRepository;
import com.sketchtrench.room.repository.RoomRepository;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * The game engine. Runs one {@link GameSession} per room in memory and drives the state
 * machine: start -> (word selection -> drawing -> guessing -> scoring) x N rounds -> end.
 *
 * <p>Why in-memory? The round loop is a hot path with sub-second timing requirements; a
 * DB round-trip per tick would be wasteful and slow. The DB gets the AUDIT TRAIL
 * (rounds, guesses, scores, drawing snapshots, game history) — everything a player or
 * analyst could ever want to re-read — while the live heartbeat stays in RAM.
 * Scaling horizontally would move the session to Redis (see deployment guide).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private static final Duration WORD_PICK_WINDOW = Duration.ofSeconds(10);
    private static final int GUESS_BASE_POINTS = 100;
    private static final int GUESS_TIME_BONUS_PER_SEC = 2;
    private static final int DRAWER_BONUS_PER_NON_GUESSER = 50;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final WordCategoryRepository categoryRepository;
    private final RoomCustomWordRepository customWordRepository;
    private final RoundRepository roundRepository;
    private final GuessRepository guessRepository;
    private final ScoreRepository scoreRepository;
    private final DrawingRepository drawingRepository;
    private final RoomPublisher publisher;
    private final TaskScheduler taskScheduler;
    private final TransactionTemplate transactionTemplate;
    private final ProgressTracker progressTracker;
    private final ObjectMapper objectMapper;

    private final Map<Long, GameSession> sessions = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // ==========================================================================
    // Entry points (request threads: transaction-bound via @Transactional)
    // ==========================================================================

    @Transactional
    public void startGame(Long roomId, Long hostId) {
        Room room = roomRepository.findWithDetailsById(roomId)
                .orElseThrow(() -> new NotFoundException("Room", roomId));
        if (!room.getHost().getId().equals(hostId)) {
            throw new ConflictException("NOT_HOST", "Only the host can start the game");
        }
        if (room.getStatus() != Room.Status.WAITING) {
            throw new ConflictException("GAME_ALREADY_RUNNING", "A game is already in progress");
        }

        List<RoomMember> players = room.getMembers().stream()
                .filter(m -> m.getRole() != RoomMember.Role.SPECTATOR)
                .sorted(Comparator.comparing(RoomMember::getJoinedAt))
                .toList();
        if (players.size() < 2) {
            throw new ConflictException("NOT_ENOUGH_PLAYERS", "Need at least 2 players to start");
        }
        for (RoomMember member : players) {
            if (!member.isReady() && member.getRole() != RoomMember.Role.HOST) {
                throw new ConflictException("NOT_READY", "Not all players are ready");
            }
        }

        int drawingTime = room.getSettings() == null ? 80 : room.getSettings().getDrawingTimeSec();
        if (room.getMode() == Room.Mode.RAPID) {
            drawingTime = Math.max(15, drawingTime / 2);
        }
        int totalRounds = room.getSettings() == null ? 3 : room.getSettings().getRounds();

        List<Long> order = players.stream().map(m -> m.getUser().getId()).toList();
        GameSession session = new GameSession(roomId, order, totalRounds, drawingTime,
                room.getMode() == Room.Mode.RANKED, room.getMode().name());
        sessions.put(roomId, session);

        // custom-words games need a pool of at least 3 before we can start
        if (room.getSettings() != null && room.getSettings().isCustomWords()
                && customWordRepository.countByRoomId(roomId) < 3) {
            sessions.remove(roomId);
            throw new ConflictException("NOT_ENOUGH_CUSTOM_WORDS",
                    "Add at least 3 custom words before starting");
        }

        // fresh slate: drop any previous game's play data for this room
        purgeGameData(roomId);

        room.setStatus(Room.Status.PLAYING);
        room.setCurrentRound(1);
        roomRepository.save(room);

        Map<Long, String> names = new HashMap<>();
        players.forEach(m -> names.put(m.getUser().getId(), m.getUser().getUsername()));
        publisher.gameUpdate(roomId, new GameDto.GameStarted(totalRounds, order, names));
        publisher.roomUpdate(roomId, com.sketchtrench.room.dto.RoomDto.RoomResponse.from(room));

        log.info("Game started in room {} ({})", roomId, room.getMode());
        beginRound(roomId, session);
    }

    /** The drawer picks one of the word options sent to them privately. */
    @Transactional
    public void selectWord(Long roomId, Long drawerId, Long wordId) {
        GameSession s = requireSession(roomId);
        if (!drawerId.equals(s.drawerId)) {
            return; // only the drawer may pick
        }
        if (s.word != null) {
            return; // already picked (auto-pick won the race)
        }
        if (!s.wordOptions.contains(wordId) || !s.wordTextById.containsKey(wordId)) {
            return;
        }
        pickWord(roomId, s, wordId);
    }

    @Transactional
    public GameDto.CorrectGuess submitGuess(Long roomId, Long userId, String text) {
        GameSession s = requireSession(roomId);
        if (s.word == null || s.currentRoundId == null) {
            throw new ConflictException("NO_ROUND", "No active round to guess");
        }
        if (userId.equals(s.drawerId)) {
            throw new ConflictException("DRAWER_CANNOT_GUESS", "The drawer cannot guess their own word");
        }
        if (s.correctGuessers.contains(userId)) {
            throw new ConflictException("ALREADY_GUESSED", "You already guessed correctly");
        }

        String guessText = normalize(text == null ? "" : text);
        String answer = normalize(s.word);
        boolean correct = guessText.equals(answer) || levenshtein(guessText, answer) <= 1;
        int distance = levenshtein(guessText, answer);

        Guess guess = new Guess();
        guess.setRound(roundRepository.findById(s.currentRoundId).orElseThrow());
        guess.setUser(userRepository.getReferenceById(userId));
        guess.setGuessedWord(text);
        guess.setCorrect(correct);
        guess.setDistance((short) distance);
        guess.setPoints(correct ? awardPoints(s) : 0);
        guess.setCreatedAt(Instant.now());
        guessRepository.save(guess);

        if (!correct) {
            publisher.chat(roomId, new GameDto.ChatMessage(userId, null, text, Instant.now()));
            return null;
        }

        s.correctGuessers.add(userId);
        int points = awardPoints(s);
        s.scores.merge(userId, points, Integer::sum);
        s.roundEndsAt = null; // stop the clock for this user's score already banked

        Score score = new Score();
        score.setRoom(roomRepository.getReferenceById(roomId));
        score.setUser(userRepository.getReferenceById(userId));
        score.setRound(roundRepository.getReferenceById(s.currentRoundId));
        score.setPoints(points);
        score.setReason("GUESS");
        score.setCreatedAt(Instant.now());
        scoreRepository.save(score);

        int remainingGuessers = s.nonDrawers() - s.correctGuessers.size();
        GameDto.CorrectGuess result =
                new GameDto.CorrectGuess(userId, username(userId), points, remainingGuessers);
        publisher.gameUpdate(roomId, result);
        progressTracker.onCorrectGuess(userId, (int) s.remainingSeconds());

        if (s.correctGuessers.size() >= s.nonDrawers()) {
            endRound(roomId, s);
        }
        return result;
    }

    // ==========================================================================
    // Drawing + chat fan-out (no state machine changes, no DB writes)
    // ==========================================================================

    /** Snapshot of a live game so a client that refreshed mid-game can rejoin. */
    @Transactional(readOnly = true)
    public GameDto.GameState getState(Long roomId, Long userId) {
        GameSession s = sessions.get(roomId);
        if (s == null || s.currentRoundId == null) {
            return new GameDto.GameState(0, null, null, 0, 0, Map.of(), null, List.of(), false);
        }
        return new GameDto.GameState(
                s.currentRoundNumber(), s.drawerId, username(s.drawerId), s.drawingTimeSec,
                (int) s.remainingSeconds(), new LinkedHashMap<>(s.scores),
                userId.equals(s.drawerId) ? s.word : null,
                List.copyOf(s.strokes), true);
    }

    public void forwardStroke(Long roomId, Long userId, Map<String, Object> stroke) {
        GameSession s = sessions.get(roomId);
        if (s != null && userId.equals(s.drawerId) && s.word != null) {
            s.strokes.add(stroke);
        }
        publisher.drawing(roomId, stroke);
    }

    public void chat(Long roomId, Long userId, String text) {
        publisher.chat(roomId, new GameDto.ChatMessage(userId, username(userId),
                ChatFilter.sanitize(text), Instant.now()));
    }

    public void typing(Long roomId, Long userId) {
        publisher.gameUpdate(roomId, new GameDto.TypingIndicator(userId, username(userId)));
    }

    // ==========================================================================
    // Round lifecycle (scheduler threads: persistence via TransactionTemplate)
    // ==========================================================================

    private void beginRound(Long roomId, GameSession s) {
        if (s.roundIndex >= s.totalRounds) {
            endGame(roomId, s);
            return;
        }

        s.drawerId = s.playerIds.get(s.roundIndex % s.playerIds.size());
        s.correctGuessers = new java.util.HashSet<>();
        s.strokes = new ArrayList<>();
        s.word = null;
        s.wordTextById = new HashMap<>();
        s.roundEndsAt = null;

        List<GameDto.WordOption> options = pickWordOptions(roomId);
        s.wordOptions = options.stream().map(GameDto.WordOption::id).toList();
        options.forEach(o -> s.wordTextById.put(o.id(), o.text()));

        String drawerName = username(s.drawerId);
        publisher.wordOptions(drawerName, new GameDto.WordOptions(s.currentRoundNumber(), options));
        publisher.gameUpdate(roomId, new GameDto.RoundStarting(s.currentRoundNumber(), s.drawerId,
                drawerName, options.size()));

        // Auto-pick if the drawer doesn't choose within the window.
        schedule(WORD_PICK_WINDOW, () -> {
            if (sessions.get(roomId) == s && s.word == null && !s.wordOptions.isEmpty()) {
                pickWord(roomId, s, s.wordOptions.get(0));
            }
        });
    }

    private void pickWord(Long roomId, GameSession s, Long wordId) {
        s.word = s.wordTextById.get(wordId);

        transactionTemplate.executeWithoutResult(tx -> {
            // custom words aren't rows in `words`; only bump usage for real pool words
            if (wordRepository.existsById(wordId)) {
                wordRepository.incrementTimesUsed(List.of(wordId));
            }
            Round round = new Round();
            round.setRoom(roomRepository.getReferenceById(roomId));
            if (wordRepository.existsById(wordId)) {
                round.setWord(wordRepository.getReferenceById(wordId));
            }
            round.setDrawer(userRepository.getReferenceById(s.drawerId));
            round.setRoundNum(s.currentRoundNumber());
            round.setStartedAt(Instant.now());
            s.currentRoundId = roundRepository.save(round).getId();
        });

        s.roundEndsAt = Instant.now().plusSeconds(s.drawingTimeSec);
        publisher.secretWord(username(s.drawerId), new GameDto.SecretWord(s.word));
        publisher.gameUpdate(roomId, new GameDto.RoundStarted(s.currentRoundNumber(), s.drawerId,
                username(s.drawerId), s.drawingTimeSec));

        s.endRoundTask = schedule(Duration.ofSeconds(s.drawingTimeSec), () -> endRound(roomId, s));
        s.tickerTask = scheduleAtFixedRate(() -> {
            if (sessions.get(roomId) == s) {
                publisher.gameUpdate(roomId, new GameDto.TimerUpdate((int) s.remainingSeconds()));
            }
        }, Duration.ofSeconds(1));
        log.info("Round {} in room {}: drawer={} word={}", s.currentRoundNumber(), roomId, s.drawerId, s.word);
    }

    private void endRound(Long roomId, GameSession s) {
        cancelTimers(s);

        String word = s.word;
        Long drawerId = s.drawerId;
        int drawerBonus = 0;
        if (word != null) {
            int nonGuessers = s.nonDrawers() - s.correctGuessers.size();
            drawerBonus = Math.max(0, nonGuessers) * DRAWER_BONUS_PER_NON_GUESSER;
            if (drawerBonus > 0) {
                s.scores.merge(drawerId, drawerBonus, Integer::sum);
            }
        }
        int bonus = drawerBonus;
        String revealed = word == null ? "(round skipped)" : word;

        final Long roundId = s.currentRoundId;
        transactionTemplate.executeWithoutResult(tx -> {
            roundRepository.findById(roundId).ifPresent(r -> {
                r.setStatus(Round.Status.ENDED);
                r.setEndedAt(Instant.now());
            });
            roomRepository.findById(roomId).ifPresent(r -> r.setCurrentRound(s.currentRoundNumber()));
            if (bonus > 0) {
                Score sc = new Score();
                sc.setRoom(roomRepository.getReferenceById(roomId));
                sc.setUser(userRepository.getReferenceById(drawerId));
                sc.setRound(roundRepository.getReferenceById(roundId));
                sc.setPoints(bonus);
                sc.setReason("WORD_BONUS");
                sc.setCreatedAt(Instant.now());
                scoreRepository.save(sc);
            }
            persistDrawingSnapshot(roomId, roundId, drawerId, s.strokes);
            persistMemberScores(roomId, s);
        });

        publisher.gameUpdate(roomId, new GameDto.RoundEnded(s.currentRoundNumber(), revealed,
                drawerId, bonus, new LinkedHashMap<>(s.scores)));

        s.roundIndex++;
        beginRound(roomId, s);
    }

    private void endGame(Long roomId, GameSession s) {
        sessions.remove(roomId);

        var entries = s.scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .toList();
        Long winnerId = entries.isEmpty() ? null : entries.get(0).getKey();
        String winnerName = winnerId == null ? null : username(winnerId);

        List<ProgressTracker.PlayerResult> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Long userId = entries.get(i).getKey();
            int eloBefore = userRepository.findById(userId).map(User::getEloRating).orElse(1200);
            results.add(new ProgressTracker.PlayerResult(userId, i + 1, entries.get(i).getValue(), eloBefore));
        }

        transactionTemplate.executeWithoutResult(tx -> {
            roomRepository.findById(roomId).ifPresent(r -> {
                r.setStatus(Room.Status.FINISHED);
                r.setCurrentRound(0);
            });
            progressTracker.onGameFinished(new ProgressTracker.GameSummary(
                    roomId, s.mode, winnerId, (int) Duration.between(s.startedAt, Instant.now()).toSeconds(),
                    results));
        });

        publisher.gameUpdate(roomId, new GameDto.GameEnded(winnerId, winnerName, new LinkedHashMap<>(s.scores)));
        publisher.roomUpdate(roomId, com.sketchtrench.room.dto.RoomDto.RoomResponse.from(
                roomRepository.findWithDetailsById(roomId).orElseThrow()));
        log.info("Game ended in room {}; winner={}", roomId, winnerId);
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private GameSession requireSession(Long roomId) {
        GameSession session = sessions.get(roomId);
        if (session == null) {
            throw new NotFoundException("Active game", roomId);
        }
        return session;
    }

    private List<GameDto.WordOption> pickWordOptions(Long roomId) {
        boolean customWords = roomRepository.findById(roomId)
                .map(r -> r.getSettings() != null && r.getSettings().isCustomWords())
                .orElse(false);
        if (customWords) {
            List<RoomCustomWord> pool = customWordRepository.findByRoomIdOrderByIdAsc(roomId);
            if (pool.size() < 3) {
                return List.of();
            }
            List<RoomCustomWord> picks = new ArrayList<>();
            List<RoomCustomWord> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, random);
            for (int i = 0; i < 3 && i < shuffled.size(); i++) {
                picks.add(shuffled.get(i));
            }
            return picks.stream()
                    .map(cw -> new GameDto.WordOption(cw.getId(), cw.getWord())).toList();
        }
        List<Long> categories = categoryRepository.findAll().stream()
                .filter(c -> c.isActive()).map(WordCategory::getId).toList();
        if (categories.isEmpty()) {
            return List.of();
        }
        Long categoryId = categories.get(random.nextInt(categories.size()));
        return wordRepository.findFreshestByCategory(categoryId, 3).stream()
                .map(w -> new GameDto.WordOption(w.getId(), w.getText())).toList();
    }

    private int awardPoints(GameSession s) {
        return GUESS_BASE_POINTS + (int) s.remainingSeconds() * GUESS_TIME_BONUS_PER_SEC;
    }

    private void purgeGameData(Long roomId) {
        guessRepository.deleteByRoundRoomId(roomId);
        drawingRepository.deleteByRoundRoomId(roomId);
        scoreRepository.deleteByRoomId(roomId);
        roundRepository.deleteByRoomId(roomId);
    }

    private void persistDrawingSnapshot(Long roomId, Long roundId, Long drawerId, List<Object> strokes) {
        if (strokes == null || strokes.isEmpty()) {
            return;
        }
        try {
            Drawing drawing = new Drawing();
            drawing.setRound(roundRepository.getReferenceById(roundId));
            drawing.setDrawer(userRepository.getReferenceById(drawerId));
            drawing.setData(objectMapper.writeValueAsString(strokes));
            drawing.setCreatedAt(Instant.now());
            drawingRepository.save(drawing);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize drawing snapshot for round {}", roundId, e);
        }
    }

    private void persistMemberScores(Long roomId, GameSession s) {
        for (RoomMember member : memberRepository.findByRoomId(roomId)) {
            Integer score = s.scores.get(member.getUser().getId());
            if (score != null) {
                member.setScore(score);
            }
        }
    }

    private void cancelTimers(GameSession s) {
        if (s.endRoundTask != null) {
            s.endRoundTask.cancel(false);
        }
        if (s.tickerTask != null) {
            s.tickerTask.cancel(false);
        }
    }

    private ScheduledFuture<?> schedule(Duration delay, Runnable task) {
        return taskScheduler.schedule(task, Instant.now().plus(delay));
    }

    private ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return taskScheduler.scheduleAtFixedRate(task, period);
    }

    private String username(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("player");
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Classic Levenshtein distance — lenient matching tolerates a typo'd guess. */
    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    /** Minimal profanity filter: blocklist replaced with asterisks. */
    public static final class ChatFilter {
        private static final List<String> BANNED = List.of("fuck", "shit", "bitch", "asshole", "cunt", "nigger");

        private ChatFilter() {
        }

        public static String sanitize(String text) {
            String result = text;
            for (String word : BANNED) {
                result = result.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(word) + "\\b",
                        "*".repeat(word.length()));
            }
            return result;
        }
    }
}
