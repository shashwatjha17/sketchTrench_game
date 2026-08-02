package com.sketchtrench.room.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.room.dto.RoomDto;
import com.sketchtrench.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Room lifecycle over REST. The lobby/game updates broadcast over WebSocket even though
 * the triggers are HTTP — REST for guaranteed delivery of the command, WS for fan-out.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public List<RoomDto.RoomResponse> listPublic() {
        return roomService.listPublic();
    }

    @PostMapping
    public ResponseEntity<RoomDto.RoomResponse> create(@Valid @RequestBody RoomDto.CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public RoomDto.RoomResponse get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @GetMapping("/{id}/words")
    public List<RoomDto.CustomWordResponse> customWords(@PathVariable Long id) {
        return roomService.listCustomWords(id);
    }

    @PostMapping("/{id}/words")
    public List<RoomDto.CustomWordResponse> addCustomWords(@PathVariable Long id,
                                                          @Valid @RequestBody RoomDto.AddCustomWordsRequest request) {
        return roomService.addCustomWords(id, SecurityUtils.currentUserId(), request);
    }

    @DeleteMapping("/{id}/words/{wordId}")
    public ResponseEntity<Void> removeCustomWord(@PathVariable Long id, @PathVariable Long wordId) {
        roomService.removeCustomWord(id, SecurityUtils.currentUserId(), wordId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    public RoomDto.RoomResponse join(@PathVariable Long id, @RequestBody(required = false)
                                     RoomDto.JoinRoomRequest request) {
        return roomService.join(id, SecurityUtils.currentUserId(),
                request == null ? new RoomDto.JoinRoomRequest(null, null) : request);
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id) {
        roomService.leave(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ready")
    public RoomDto.RoomResponse ready(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean ready) {
        return roomService.setReady(id, SecurityUtils.currentUserId(), ready);
    }

    @PutMapping("/{id}")
    public RoomDto.RoomResponse update(@PathVariable Long id,
                                       @Valid @RequestBody RoomDto.CreateRoomRequest request) {
        return roomService.update(id, SecurityUtils.currentUserId(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/kick/{userId}")
    public RoomDto.RoomResponse kick(@PathVariable Long id, @PathVariable Long userId) {
        return roomService.kick(id, SecurityUtils.currentUserId(), userId);
    }

    @PostMapping("/{id}/ban/{userId}")
    public RoomDto.RoomResponse ban(@PathVariable Long id, @PathVariable Long userId) {
        return roomService.ban(id, SecurityUtils.currentUserId(), userId);
    }

    @PostMapping("/{id}/transfer-host/{userId}")
    public RoomDto.RoomResponse transferHost(@PathVariable Long id, @PathVariable Long userId) {
        return roomService.transferHost(id, SecurityUtils.currentUserId(), userId);
    }
}
