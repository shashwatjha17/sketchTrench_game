package com.sketchtrench.social.dto;

import com.sketchtrench.social.entity.Notification;

import java.time.Instant;

public record NotificationDto() {

    public record NotificationResponse(
            Long id,
            String type,
            String title,
            String body,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getType(),
                    notification.getTitle(), notification.getBody(),
                    notification.isRead(), notification.getCreatedAt());
        }
    }
}
