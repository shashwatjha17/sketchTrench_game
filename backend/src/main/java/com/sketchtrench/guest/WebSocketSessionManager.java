package com.sketchtrench.guest;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps WebSocket session ids to guest players so inbound STOMP frames can be tied to a player
 * without a JWT. The {@link GuestChannelInterceptor} populates it on CONNECT.
 */
@Component
public class WebSocketSessionManager {

    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> playerToSession = new ConcurrentHashMap<>();

    public void bind(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        playerToSession.put(playerId, sessionId);
    }

    public void unbind(String sessionId) {
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null) {
            playerToSession.remove(playerId, sessionId);
        }
    }

    public String playerForSession(String sessionId) {
        return sessionId == null ? null : sessionToPlayer.get(sessionId);
    }

    public String sessionForPlayer(String playerId) {
        return playerId == null ? null : playerToSession.get(playerId);
    }

    public boolean isConnected(String playerId) {
        return playerToSession.containsKey(playerId);
    }
}
