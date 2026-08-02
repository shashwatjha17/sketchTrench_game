package com.sketchtrench.social.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.social.dto.FriendDto;
import com.sketchtrench.social.service.FriendService;
import com.sketchtrench.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public ResponseEntity<FriendDto.FriendRequestResponse> send(@Valid @RequestBody FriendDto.SendFriendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(friendService.sendRequest(SecurityUtils.currentUserId(), request.receiverId()));
    }

    @GetMapping("/requests")
    public List<FriendDto.FriendRequestResponse> incoming() {
        return friendService.incoming(SecurityUtils.currentUserId());
    }

    @PostMapping("/requests/{id}/accept")
    public FriendDto.FriendRequestResponse accept(@PathVariable Long id) {
        return friendService.accept(id, SecurityUtils.currentUserId());
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id) {
        friendService.decline(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        friendService.cancel(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UserResponse> friends() {
        return friendService.listFriends(SecurityUtils.currentUserId());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@PathVariable Long userId) {
        friendService.removeFriend(SecurityUtils.currentUserId(), userId);
        return ResponseEntity.noContent().build();
    }
}
