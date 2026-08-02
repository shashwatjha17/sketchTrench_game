package com.sketchtrench.guest;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All live guest sessions. Keyed by playerId; reconnect tokens index the same players so a
 * page refresh can resume a session. Everything here is gone when the server stops.
 */
@Component
public class PlayerSessionManager {

    private final Map<String, GuestPlayer> byId = new ConcurrentHashMap<>();
    private final Map<String, GuestPlayer> byToken = new ConcurrentHashMap<>();

    public GuestPlayer create(GuestDto.SessionRequest req) {
        GuestPlayer player = new GuestPlayer(
                req.nickname() == null || req.nickname().isBlank() ? "Guest" : req.nickname().trim(),
                req.avatarColor(), req.avatarExpression(), req.avatarSunglasses(), req.avatarWig(),
                req.language() == null || req.language().isBlank() ? "en" : req.language());
        byId.put(player.playerId, player);
        byToken.put(player.reconnectToken, player);
        return player;
    }

    public GuestPlayer get(String playerId) {
        return playerId == null ? null : byId.get(playerId);
    }

    public GuestPlayer byReconnectToken(String token) {
        return token == null ? null : byToken.get(token);
    }

    public void remove(GuestPlayer player) {
        byId.remove(player.playerId);
        byToken.remove(player.reconnectToken);
    }

    public int size() {
        return byId.size();
    }
}
