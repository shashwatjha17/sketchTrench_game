package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DrawerRotationTest {

    private static GuestPlayer player(String name, boolean bot) {
        GuestPlayer p = new GuestPlayer(name, "#fff", "happy", false, "none", "en");
        p.isBot = bot;
        return p;
    }

    private static GameRoom room(GuestPlayer... players) {
        GameRoom room = new GameRoom("r1", "Room", false, 8, 6, 60, false);
        for (GuestPlayer p : players) room.players.put(p.playerId, p);
        return room;
    }

    @Test
    void rotatesThroughHumansAndSkipsBots() {
        GuestPlayer a = player("a", false), bot = player("bot", true), b = player("b", false);
        GameRoom room = room(a, bot, b);
        assertEquals(a, room.nextDrawer());
        assertEquals(b, room.nextDrawer());
        assertEquals(a, room.nextDrawer()); // new rotation
    }

    @Test
    void lateJoinerGetsATurnWithoutSkippingAnyone() {
        GuestPlayer a = player("a", false), b = player("b", false), c = player("c", false);
        GameRoom room = room(a, b);
        assertEquals(a, room.nextDrawer());
        room.players.put(c.playerId, c); // joins mid-game
        assertEquals(b, room.nextDrawer());
        assertEquals(c, room.nextDrawer());
        assertEquals(a, room.nextDrawer());
    }

    @Test
    void leaverDoesNotBlockTheRotation() {
        GuestPlayer a = player("a", false), b = player("b", false), c = player("c", false);
        GameRoom room = room(a, b, c);
        assertEquals(a, room.nextDrawer());
        room.players.remove(b.playerId);
        assertEquals(c, room.nextDrawer());
        assertEquals(a, room.nextDrawer());
    }

    @Test
    void noHumansNoDrawer() {
        assertNull(room(player("bot", true)).nextDrawer());
    }
}
