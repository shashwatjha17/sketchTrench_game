package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A non-member's SUBSCRIBE must be dropped (null), never throw: throwing closes the socket. */
class ChannelInterceptorTest {

    private final PlayerSessionManager players = new PlayerSessionManager();
    private final RoomManager rooms = new RoomManager();
    private final GuestChannelInterceptor interceptor =
            new GuestChannelInterceptor(players, rooms, new WebSocketSessionManager());

    private Message<byte[]> subscribe(String playerId, String destination) {
        StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        a.setDestination(destination);
        a.setUser(() -> playerId);
        a.setSubscriptionId("sub-0");
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private GuestPlayer player(String name) {
        return players.create(new GuestDto.SessionRequest(name, "#fff", "happy", false, "none", "en"));
    }

    @Test
    void membersMaySubscribeToTheirRoom() {
        GuestPlayer host = player("host");
        GameRoom room = rooms.create(new GuestDto.CreateRoomRequest("r", true, 8, 3, 60, false, null, null, null), host);
        room.players.put(host.playerId, host);
        assertNotNull(interceptor.preSend(subscribe(host.playerId, "/topic/room/" + room.roomId), null));
        assertNotNull(interceptor.preSend(subscribe(host.playerId, "/topic/drawing/" + room.roomId), null));
    }

    @Test
    void nonMembersAreDroppedNotDisconnected() {
        GuestPlayer host = player("host");
        GuestPlayer outsider = player("out");
        GameRoom room = rooms.create(new GuestDto.CreateRoomRequest("r", true, 8, 3, 60, false, null, null, null), host);
        room.players.put(host.playerId, host);
        assertNull(interceptor.preSend(subscribe(outsider.playerId, "/topic/game/" + room.roomId), null));
        assertNull(interceptor.preSend(subscribe(outsider.playerId, "/topic/room/missing"), null));
    }

    @Test
    void privateQueuesAreAlwaysAllowed() {
        GuestPlayer p = player("p");
        assertNotNull(interceptor.preSend(subscribe(p.playerId, "/user/queue/kicked"), null));
    }
}
