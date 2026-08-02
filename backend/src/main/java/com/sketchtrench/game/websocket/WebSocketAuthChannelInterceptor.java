package com.sketchtrench.game.websocket;

import com.sketchtrench.auth.security.JwtService;
import com.sketchtrench.auth.security.UserPrincipal;
import com.sketchtrench.room.repository.RoomMemberRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * WebSocket security. Two jobs:
 *  1. CONNECT — authenticate the JWT (sent as a STOMP header) and install the
 *     {@link UserPrincipal} as the STOMP session user. Unauthenticated connections fail.
 *  2. SUBSCRIBE — only room members may subscribe to that room's topics; private
 *     /user/ destinations are inherently scoped to the authenticated user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final RoomMemberRepository memberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        // STOMP 1.2 keeps the space after the colon in the value (" Bearer ..."), so trim.
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null) {
            auth = auth.trim();
        }
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing or invalid token");
        }
        try {
            Claims claims = jwtService.parse(auth.substring(7));
            UserPrincipal principal = new UserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class));
            accessor.setUser(principal);
        } catch (Exception e) {
            throw new AccessDeniedException("Invalid token", e);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        // /user/{name}/queue/* is already private — the broker only routes your own.
        if (destination.startsWith("/user/")) {
            return;
        }
        // Every room-scoped topic is gated on membership.
        Long roomId = extractRoomId(destination);
        if (roomId != null) {
            Long userId = ((UserPrincipal) accessor.getUser()).id();
            boolean member = memberRepository.findByRoomIdAndUserId(roomId, userId).isPresent();
            if (!member) {
                log.warn("User {} denied subscribe to {} (not a member)", userId, destination);
                throw new AccessDeniedException("Not a member of this room");
            }
        }
    }

    private Long extractRoomId(String destination) {
        for (String prefix : new String[]{"/topic/room/", "/topic/game/", "/topic/drawing/", "/topic/chat/"}) {
            if (destination.startsWith(prefix)) {
                try {
                    return Long.parseLong(destination.substring(prefix.length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
