package com.sketchtrench.game.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * In-memory state of one running game. Lives in a map in {@code GameService}; the DB
 * persists the audit trail (rounds/guesses/scores/history), this object holds the live,
 * hot path. Volatile fields are touched by the scheduler threads.
 */
public class GameSession {

    public final Long roomId;
    public final List<Long> playerIds;
    public final Map<Long, Integer> scores = new LinkedHashMap<>();
    public final int totalRounds;
    public final int drawingTimeSec;
    public final boolean ranked;
    public final String mode;

    public volatile int roundIndex;
    public volatile Long drawerId;
    public volatile Long currentRoundId;
    public volatile String word;
    public volatile List<Long> wordOptions = List.of();
    public volatile Map<Long, String> wordTextById = Map.of();
    public volatile Set<Long> correctGuessers = new HashSet<>();
    public volatile List<Object> strokes = new ArrayList<>();
    public volatile Instant roundEndsAt;
    public volatile ScheduledFuture<?> endRoundTask;
    public volatile ScheduledFuture<?> tickerTask;
    public volatile Instant startedAt;

    public GameSession(Long roomId, List<Long> playerIds, int totalRounds, int drawingTimeSec,
                       boolean ranked, String mode) {
        this.roomId = roomId;
        this.playerIds = playerIds;
        this.totalRounds = totalRounds;
        this.drawingTimeSec = drawingTimeSec;
        this.ranked = ranked;
        this.mode = mode;
        this.startedAt = Instant.now();
        playerIds.forEach(id -> scores.put(id, 0));
    }

    public int currentRoundNumber() {
        return roundIndex + 1;
    }

    public int nonDrawers() {
        return Math.max(playerIds.size() - 1, 1);
    }

    public long remainingSeconds() {
        if (roundEndsAt == null) {
            return 0L;
        }
        return Math.max(0, java.time.Duration.between(Instant.now(), roundEndsAt).toSeconds());
    }
}
