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
        String name = req.name() == null || req.name().isBlank() ? host.nickname + "'s room" : req.name().trim();
        if (name.length() > 40) name = name.substring(0, 40);
        if (ChatFilter.containsProfanity(name)) name = host.nickname + "'s room";
        GameRoom room = new GameRoom(
                java.util.UUID.randomUUID().toString().substring(0, 8),
                name,
                req.isPrivate(),
                req.maxPlayers() > 0 ? Math.min(req.maxPlayers(), 12) : 8,
                req.totalRounds() > 0 ? Math.min(req.totalRounds(), 10) : 3,
                req.drawingTimeSec() > 0 ? Math.min(req.drawingTimeSec(), 300) : 80,
                req.customWords());
        room.language = WordPool.normalizeLanguage(req.language() != null ? req.language() : host.language);
        room.hintsEnabled = req.hints() == null || req.hints();
        if (req.customWordList() != null) {
            req.customWordList().stream().map(String::trim)
                    .filter(w -> !w.isEmpty() && w.length() <= 64 && !ChatFilter.containsProfanity(w))
                    .distinct().limit(200).forEach(room.customWords::add);
        }
        rooms.put(room.roomId, room);
        return room;
    }

    public GameRoom get(String roomId) {
        return roomId == null ? null : rooms.get(roomId);
    }

    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    /**
     * Public rooms anyone can join from the lobby: waiting or mid-game, not full, with at
     * least one human in them. Waiting rooms first, then fullest first.
     */
    public List<GameRoom> publicOpen() {
        return rooms.values().stream()
                .filter(r -> !r.isPrivate && !"FINISHED".equals(r.status))
                .filter(r -> r.playerCount() < r.maxPlayers && !r.humanPlayers().isEmpty())
                .sorted(java.util.Comparator
                        .comparing((GameRoom r) -> !"WAITING".equals(r.status))
                        .thenComparing(GameRoom::playerCount, java.util.Comparator.reverseOrder()))
                .toList();
    }
}
