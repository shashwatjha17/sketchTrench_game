package com.sketchtrench.guest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/** One in-memory room and its live game state. No persistence anywhere. */
public class GameRoom {

    public final String roomId;
    public final String name;
    public final boolean isPrivate;
    public final int maxPlayers;
    public final int totalRounds;
    public final int drawingTimeSec;
    public final boolean customWordsEnabled;
    public final List<String> customWords = new ArrayList<>();

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
    public volatile Instant roundEndsAt;
    public volatile ScheduledFuture<?> endRoundTask;
    public volatile ScheduledFuture<?> tickerTask;
    public volatile Instant startedAt;

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
}
