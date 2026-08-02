package com.sketchtrench.game.websocket;

import com.sketchtrench.auth.security.UserPrincipal;
import com.sketchtrench.user.entity.OnlineStatus;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presence: flips a user's online_status when their socket connects/disconnects.
 * Session IDs are tracked so reconnect clears the right ghost entry.
 *
 * <p>This is the same pattern an online-status feature in the profile uses; for extra
 * scale it would move to Redis pub/sub, but in-memory is correct for one instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final UserRepository userRepository;
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (accessor.getUser() instanceof UserPrincipal principal) {
            sessionToUserId.put(sessionId, principal.id());
            userRepository.findById(principal.id()).ifPresent(user -> {
                user.setOnlineStatus(OnlineStatus.ONLINE);
                user.setLastSeenAt(Instant.now());
            });
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = sessionToUserId.remove(sessionId);
        if (userId != null) {
            // A reconnect may already have flipped us back ONLINE; only mark OFFLINE if no
            // other session for this user remains.
            boolean stillConnected = sessionToUserId.values().stream().anyMatch(userId::equals);
            if (!stillConnected) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setOnlineStatus(OnlineStatus.OFFLINE);
                    user.setLastSeenAt(Instant.now());
                });
            }
        }
    }
}
