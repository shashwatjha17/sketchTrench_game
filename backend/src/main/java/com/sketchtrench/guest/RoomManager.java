package com.sketchtrench.guest;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** All live rooms, in memory. Empty rooms are reaped by the caller. */
@Component
public class RoomManager {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public GameRoom create(GuestDto.CreateRoomRequest req, GuestPlayer host) {
        GameRoom room = new GameRoom(
                java.util.UUID.randomUUID().toString().substring(0, 8),
                req.name() == null || req.name().isBlank() ? "Room of " + host.nickname : req.name().trim(),
                req.isPrivate(),
                req.maxPlayers() > 0 ? Math.min(req.maxPlayers(), 12) : 8,
                req.totalRounds() > 0 ? Math.min(req.totalRounds(), 10) : 3,
                req.drawingTimeSec() > 0 ? Math.min(req.drawingTimeSec(), 300) : 80,
                req.customWords());
        room.customWords.addAll(req.customWordList() == null ? List.of() : req.customWordList());
        rooms.put(room.roomId, room);
        return room;
    }

    public GameRoom get(String roomId) {
        return roomId == null ? null : rooms.get(roomId);
    }

    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    public List<GameRoom> publicWaiting() {
        return rooms.values().stream()
                .filter(r -> !r.isPrivate && "WAITING".equals(r.status))
                .toList();
    }
}
