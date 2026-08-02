package com.sketchtrench.guest;

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
 * WebSocket security for the guest flow:
 *  1. CONNECT — reads the {@code X-Player-Id} STOMP header, looks up the guest, and
 *     installs it as the session principal. Unknown players can't connect.
 *  2. SUBSCRIBE — only members of a room may subscribe to that room's topics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestChannelInterceptor implements ChannelInterceptor {

    // Only managers here — GuestService pulls in the messaging template, which is still
    // being built when this interceptor runs (WS broker init), so it must not be a dep.
    private final PlayerSessionManager players;
    private final RoomManager rooms;
    private final WebSocketSessionManager sessionManager;

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
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();
            if (sessionId != null) {
                sessionManager.unbind(sessionId);
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String playerId = accessor.getFirstNativeHeader(GuestController.PLAYER_HEADER);
        if (playerId != null) {
            playerId = playerId.trim();
        }
        GuestPlayer player = playerId == null ? null : players.get(playerId);
        if (player == null) {
            throw new AccessDeniedException("Unknown player: create a session first");
        }
        player.isConnected = true;
        accessor.setUser(() -> player.playerId);
        if (accessor.getSessionId() != null) {
            sessionManager.bind(accessor.getSessionId(), player.playerId);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.startsWith("/user/")) {
            return;
        }
        String roomId = extractRoomId(destination);
        if (roomId != null) {
            GameRoom room = rooms.get(roomId);
            boolean member = room != null && accessor.getUser() != null && room.players.containsKey(accessor.getUser().getName());
            if (!member) {
                log.warn("Player denied subscribe to {} (not a member)", destination);
                throw new AccessDeniedException("Not a member of this room");
            }
        }
    }

    private String extractRoomId(String destination) {
        for (String prefix : new String[]{"/topic/room/", "/topic/game/", "/topic/drawing/", "/topic/chat/"}) {
            if (destination.startsWith(prefix)) {
                String rest = destination.substring(prefix.length());
                int slash = rest.indexOf('/');
                return slash == -1 ? rest : rest.substring(0, slash);
            }
        }
        return null;
    }
}
