package com.sketchtrench.social.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.social.dto.NotificationDto;
import com.sketchtrench.social.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto.NotificationResponse> inbox(@RequestParam(defaultValue = "20") int limit) {
        return notificationService.inbox(SecurityUtils.currentUserId(), limit).stream()
                .map(NotificationDto.NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return notificationService.unreadCount(SecurityUtils.currentUserId());
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id);
    }
}
