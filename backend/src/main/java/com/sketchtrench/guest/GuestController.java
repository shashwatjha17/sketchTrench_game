package com.sketchtrench.guest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the guest flow. The caller identifies themselves with the playerId
 * header (the same one issued by {@code POST /api/guest/session}), not a JWT.
 */
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestController {

    public static final String PLAYER_HEADER = "X-Player-Id";

    private final GuestService service;

    private GuestPlayer requirePlayer(String playerId) {
        GuestPlayer player = service.requirePlayer(playerId);
        if (player == null) {
            throw new com.sketchtrench.exception.ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_PLAYER", "Unknown player. Create a session first.");
        }
        return player;
    }

    // ---- sessions ----

    @PostMapping("/session")
    public Map<String, Object> session(@RequestBody GuestDto.SessionRequest req) {
        GuestPlayer player = service.createSession(req);
        // reconnectToken is the localStorage handle that lets a refresh resume the session
        return Map.of("player", service.toPlayerInfo(player),
                "reconnectToken", player.reconnectToken,
                "playerId", player.playerId);
    }

    @PostMapping("/reconnect")
    public Map<String, Object> reconnect(@RequestBody GuestDto.ReconnectRequest req) {
        return service.reconnect(req);
    }

    // ---- rooms ----

    @GetMapping("/rooms")
    public List<GuestDto.RoomInfo> listRooms() {
        return service.listPublicRooms();
    }

    @GetMapping("/rooms/{roomId}")
    public GuestDto.RoomInfo getRoom(@PathVariable String roomId) {
        return service.getRoom(roomId);
    }

    @PostMapping("/rooms")
    public GuestDto.RoomInfo createRoom(@RequestHeader(PLAYER_HEADER) String playerId,
                                        @RequestBody GuestDto.CreateRoomRequest req) {
        return service.createRoom(requirePlayer(playerId), req);
    }

    @PostMapping("/rooms/{roomId}/join")
    public GuestDto.RoomInfo joinRoom(@RequestHeader(PLAYER_HEADER) String playerId,
                                      @PathVariable String roomId) {
        return service.joinRoom(requirePlayer(playerId), roomId);
    }

    @PostMapping("/rooms/{roomId}/leave")
    public void leaveRoom(@RequestHeader(PLAYER_HEADER) String playerId, @PathVariable String roomId) {
        service.leaveRoom(requirePlayer(playerId));
    }

    @PostMapping("/rooms/{roomId}/start")
    public void startGame(@RequestHeader(PLAYER_HEADER) String playerId, @PathVariable String roomId) {
        service.startGame(requirePlayer(playerId), roomId);
    }

    @PostMapping("/rooms/{roomId}/words")
    public GuestDto.RoomInfo addWords(@RequestHeader(PLAYER_HEADER) String playerId,
                                      @PathVariable String roomId,
                                      @RequestBody GuestDto.WordRequest req) {
        GuestPlayer host = requirePlayer(playerId);
        service.addCustomWords(host, roomId, req.words());
        return service.getRoom(roomId);
    }

    @PostMapping("/rooms/{roomId}/words/remove")
    public GuestDto.RoomInfo removeWord(@RequestHeader(PLAYER_HEADER) String playerId,
                                        @PathVariable String roomId,
                                        @RequestBody Map<String, String> body) {
        GuestPlayer host = requirePlayer(playerId);
        service.removeCustomWord(host, roomId, body.get("word"));
        return service.getRoom(roomId);
    }

    // ---- game ----

    @PostMapping("/rooms/{roomId}/guess")
    public GuestDto.CorrectGuess guess(@RequestHeader(PLAYER_HEADER) String playerId,
                                       @PathVariable String roomId,
                                       @RequestBody Map<String, String> body) {
        return service.submitGuess(requirePlayer(playerId).playerId, roomId, body.get("text"));
    }

    @GetMapping("/rooms/{roomId}/state")
    public GuestDto.GameState state(@RequestHeader(PLAYER_HEADER) String playerId,
                                    @PathVariable String roomId) {
        return service.toGameState(service.requireRoom(roomId), requirePlayer(playerId));
    }
}
