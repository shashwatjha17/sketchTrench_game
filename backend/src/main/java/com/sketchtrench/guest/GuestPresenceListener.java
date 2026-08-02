package com.sketchtrench.guest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Reaps guests when their socket dies (tab closed, network drop). The {@link GuestService}
 * keeps the player around for a grace period so a page refresh / reconnect resumes the
 * same session, then removes them (and empties the room) for good.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestPresenceListener {

    private final GuestService service;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            service.onDisconnect(sessionId);
        }
    }
}
