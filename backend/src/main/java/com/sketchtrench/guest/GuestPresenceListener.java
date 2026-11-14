package com.sketchtrench.guest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Tracks guest presence from socket lifecycle events. On connect the room is re-broadcast so
 * everyone (including the player themselves) sees "connected" without a refresh. On
 * disconnect the {@link GuestService} keeps the player around for a grace period so a page
 * refresh / reconnect resumes the same session, then removes them for good.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestPresenceListener {

    private final GuestService service;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            service.onConnect(user.getName());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            service.onDisconnect(sessionId);
        }
    }
}
