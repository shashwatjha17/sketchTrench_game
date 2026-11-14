package com.sketchtrench.guest;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps WebSocket session ids to guest players so inbound STOMP frames can be tied to a player
 * without credentials. The {@link GuestChannelInterceptor} populates it on CONNECT.
 *
 * <p>A player can hold several live sessions at once (two tabs, or a reconnect that lands
 * before the old socket's disconnect is processed), so presence is "at least one session".
 */
@Component
public class WebSocketSessionManager {

    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> playerToSessions = new ConcurrentHashMap<>();

    public void bind(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        playerToSessions.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /** Removes the session and returns the player it belonged to (or null). */
    public String unbind(String sessionId) {
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null) {
            playerToSessions.computeIfPresent(playerId, (k, set) -> {
                set.remove(sessionId);
                return set.isEmpty() ? null : set;
            });
        }
        return playerId;
    }

    public String playerForSession(String sessionId) {
        return sessionId == null ? null : sessionToPlayer.get(sessionId);
    }

    public boolean isConnected(String playerId) {
        Set<String> set = playerId == null ? null : playerToSessions.get(playerId);
        return set != null && !set.isEmpty();
    }
}
