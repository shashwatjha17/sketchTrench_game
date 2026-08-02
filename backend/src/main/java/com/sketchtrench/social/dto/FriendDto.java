package com.sketchtrench.social.dto;

import com.sketchtrench.social.entity.FriendRequest;
import com.sketchtrench.user.entity.User;

import java.time.Instant;
import java.util.List;

public final class FriendDto {

    private FriendDto() {
    }

    public record SendFriendRequest(Long receiverId) {
    }

    public record FriendRequestResponse(
            Long id,
            Long senderId,
            String senderUsername,
            FriendRequest.Status status,
            Instant createdAt
    ) {
        public static FriendRequestResponse from(FriendRequest request) {
            User sender = request.getSender();
            return new FriendRequestResponse(request.getId(), sender.getId(), sender.getUsername(),
                    request.getStatus(), request.getCreatedAt());
        }
    }

    /** Pushed to /user/{name}/queue/notifications over WebSocket. */
    public record NotificationPayload(String type, String title, String body) {
    }
}
