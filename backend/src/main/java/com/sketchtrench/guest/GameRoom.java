package com.sketchtrench.guest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** One in-memory room and its live game state. No persistence anywhere. */
public class GameRoom {

    public final String roomId;
    public final String name;
    // settings: the host may change these while the room is WAITING
    public volatile boolean isPrivate;
    public volatile int maxPlayers;
    public volatile int totalRounds;
    public volatile int drawingTimeSec;
    public volatile boolean customWordsEnabled;
    public volatile String language = WordPool.DEFAULT_LANGUAGE;
    public volatile boolean hintsEnabled = true;
    public final List<String> customWords = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** words already drawn this game, so the pool doesn't repeat itself */
    public final Set<String> usedWords = ConcurrentHashMap.newKeySet();

    public final Map<String, GuestPlayer> players = new LinkedHashMap<>();
    public volatile String hostId;
    public volatile String status = "WAITING"; // WAITING | PLAYING | FINISHED

    // game state
    public volatile int roundIndex;
    public volatile String drawerId;
    public volatile String word;
    public volatile List<GuestDto.WordOption> wordOptions = List.of();
    public volatile List<String> guessed = new ArrayList<>();
    public volatile List<Object> strokes = new ArrayList<>();
    /** humans who already had a drawing turn in the current rotation (survives joins/leaves) */
    public final Set<String> drewThisCycle = ConcurrentHashMap.newKeySet();
    /** letter positions of {@link #word} revealed to guessers so far this round */
    public final Set<Integer> revealed = ConcurrentHashMap.newKeySet();
    public volatile Instant roundEndsAt;
    public volatile ScheduledFuture<?> endRoundTask;
    public volatile ScheduledFuture<?> tickerTask;
    public volatile Instant startedAt;
    /** Scheduled bot guesses/chatter and hint reveals for the current round; cancelled when the round ends. */
    public final List<ScheduledFuture<?>> botTasks = new ArrayList<>();

    // moderation: target playerId -> voters, and players kicked from this room
    public final Map<String, Set<String>> kickVotes = new ConcurrentHashMap<>();
    public final Set<String> banned = ConcurrentHashMap.newKeySet();

    public GameRoom(String roomId, String name, boolean isPrivate, int maxPlayers,
                    int totalRounds, int drawingTimeSec, boolean customWordsEnabled) {
        this.roomId = roomId;
        this.name = name;
        this.isPrivate = isPrivate;
        this.maxPlayers = maxPlayers;
        this.totalRounds = totalRounds;
        this.drawingTimeSec = drawingTimeSec;
        this.customWordsEnabled = customWordsEnabled;
    }

    public int playerCount() {
        return players.size();
    }

    public long remainingSeconds() {
        if (roundEndsAt == null) return 0;
        return Math.max(0, java.time.Duration.between(Instant.now(), roundEndsAt).toSeconds());
    }

    public List<GuestPlayer> orderedPlayers() {
        return players.values().stream().toList();
    }

    /**
     * Next drawer: the first human (join order) who hasn't drawn in this rotation. Once
     * everyone has, a new rotation starts. Late joiners slot in without anyone being skipped.
     */
    public GuestPlayer nextDrawer() {
        List<GuestPlayer> humans = humanPlayers();
        if (humans.isEmpty()) return null;
        drewThisCycle.retainAll(humans.stream().map(p -> p.playerId).toList());
        GuestPlayer next = humans.stream().filter(p -> !drewThisCycle.contains(p.playerId)).findFirst().orElse(null);
        if (next == null) {
            drewThisCycle.clear();
            next = humans.get(0);
        }
        drewThisCycle.add(next.playerId);
        return next;
    }

    /** Real players only (bots excluded) — drawers, hosts and kick electorates come from here. */
    public List<GuestPlayer> humanPlayers() {
        return players.values().stream().filter(p -> !p.isBot).toList();
    }
}
