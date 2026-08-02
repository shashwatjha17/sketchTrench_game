package com.sketchtrench.social.service;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.common.websocket.RoomPublisher;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.social.dto.FriendDto;
import com.sketchtrench.social.entity.Notification;
import com.sketchtrench.social.repository.NotificationRepository;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Notification inbox: persisted + pushed live when created. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RoomPublisher publisher;

    @Transactional
    public void notify(Long userId, String type, String title, String body) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);

        userRepository.findById(userId).ifPresent(user ->
                publisher.notifyUser(user.getUsername(),
                        new FriendDto.NotificationPayload(type, title, body)));
    }

    @Transactional(readOnly = true)
    public List<Notification> inbox(Long userId, int limit) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 100)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId));
        if (!notification.getUser().getId().equals(SecurityUtils.currentUserId())) {
            throw new NotFoundException("Notification", notificationId);
        }
        notification.setRead(true);
    }
}
