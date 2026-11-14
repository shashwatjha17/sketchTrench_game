package com.sketchtrench.guest;

import java.time.Instant;
import java.util.UUID;

/** One guest in memory. Lives only as long as the server does. */
public class GuestPlayer {

    public final String playerId;
    public final String reconnectToken;
    public final Instant createdAt;

    public volatile String nickname;
    public volatile String avatarColor;
    public volatile String avatarExpression;
    public volatile boolean avatarSunglasses;
    public volatile String avatarWig;
    public volatile String language;
    public volatile String roomId;
    public volatile int score;
    public volatile boolean isHost;
    public volatile boolean isDrawing;
    public volatile boolean isConnected;
    /** Server-side bot: lives only inside its room, guesses but never draws. */
    public volatile boolean isBot;
    public volatile String wsSessionId;
    /** chat flood guard: 5 messages per 4 seconds */
    public final RateLimiter chatLimiter = new RateLimiter(5, 4000);

    public GuestPlayer(String nickname, String avatarColor, String avatarExpression,
                       boolean avatarSunglasses, String avatarWig, String language) {
        this.playerId = UUID.randomUUID().toString();
        this.reconnectToken = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.avatarColor = avatarColor;
        this.avatarExpression = avatarExpression;
        this.avatarSunglasses = avatarSunglasses;
        this.avatarWig = avatarWig;
        this.language = language;
        this.createdAt = Instant.now();
    }
}
