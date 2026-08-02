package com.sketchtrench.social.service;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.common.websocket.RoomPublisher;
import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.progress.service.ProgressionService;
import com.sketchtrench.social.dto.FriendDto;
import com.sketchtrench.social.entity.Friend;
import com.sketchtrench.social.entity.FriendRequest;
import com.sketchtrench.social.repository.FriendRepository;
import com.sketchtrench.social.repository.FriendRequestRepository;
import com.sketchtrench.user.dto.UserResponse;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Friendship graph: send/accept/decline/cancel requests, list friends, remove friends.
 * Accepting a request creates the canonical {@code (minId, maxId)} edge — direction-free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRequestRepository requestRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final RoomPublisher publisher;
    private final ProgressionService progressionService;

    @Transactional
    public FriendDto.FriendRequestResponse sendRequest(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new ConflictException("CANNOT_ADD_SELF", "You cannot friend yourself");
        }
        if (areFriends(senderId, receiverId)) {
            throw new ConflictException("ALREADY_FRIENDS", "You are already friends");
        }
        if (requestRepository.existsBySenderIdAndReceiverIdAndStatus(senderId, receiverId,
                FriendRequest.Status.PENDING)) {
            throw new ConflictException("REQUEST_EXISTS", "Friend request already pending");
        }
        // a pending request in the opposite direction counts as a duplicate too
        if (requestRepository.existsBySenderIdAndReceiverIdAndStatus(receiverId, senderId,
                FriendRequest.Status.PENDING)) {
            throw new ConflictException("REQUEST_EXISTS", "This player already sent you a request");
        }

        FriendRequest request = new FriendRequest();
        request.setSender(userRepository.getReferenceById(senderId));
        request.setReceiver(userRepository.getReferenceById(receiverId));
        request.setCreatedAt(Instant.now());
        FriendRequest saved = requestRepository.save(request);

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new NotFoundException("User", receiverId));
        publisher.notifyUser(receiver.getUsername(), new FriendDto.NotificationPayload(
                "FRIEND_REQUEST", "New friend request",
                userRepository.findById(senderId).map(User::getUsername).orElse("Someone") + " sent you a request"));
        return FriendDto.FriendRequestResponse.from(saved);
    }

    @Transactional
    public FriendDto.FriendRequestResponse accept(Long requestId, Long receiverId) {
        FriendRequest request = loadForReceiver(requestId, receiverId);
        request.setStatus(FriendRequest.Status.ACCEPTED);
        request.setRespondedAt(Instant.now());

        Long a = Math.min(request.getSender().getId(), request.getReceiver().getId());
        Long b = Math.max(request.getSender().getId(), request.getReceiver().getId());
        if (!friendRepository.existsByUserAIdAndUserBId(a, b)) {
            Friend friend = new Friend(userRepository.getReferenceById(a),
                    userRepository.getReferenceById(b), Instant.now());
            friendRepository.save(friend);
        }

        progressionService.onFriendAdded(request.getSender().getId());
        progressionService.onFriendAdded(receiverId);

        User sender = request.getSender();
        publisher.notifyUser(sender.getUsername(), new FriendDto.NotificationPayload(
                "FRIEND_ACCEPTED", "Friend request accepted",
                userRepository.findById(receiverId).map(User::getUsername).orElse("Someone") + " accepted your request"));
        return FriendDto.FriendRequestResponse.from(request);
    }

    @Transactional
    public void decline(Long requestId, Long receiverId) {
        FriendRequest request = loadForReceiver(requestId, receiverId);
        request.setStatus(FriendRequest.Status.DECLINED);
        request.setRespondedAt(Instant.now());
    }

    @Transactional
    public void cancel(Long requestId, Long senderId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request", requestId));
        if (!request.getSender().getId().equals(senderId)) {
            throw new ConflictException("NOT_YOUR_REQUEST", "Not your request to cancel");
        }
        request.setStatus(FriendRequest.Status.CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listFriends(Long userId) {
        List<Friend> edges = friendRepository.findByUserAIdOrUserBId(userId, userId);
        return edges.stream()
                .map(edge -> edge.getUserA().getId().equals(userId) ? edge.getUserB() : edge.getUserA())
                .sorted(Comparator.comparing(User::getUsername))
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendDto.FriendRequestResponse> incoming(Long userId) {
        return requestRepository.findByReceiverIdAndStatus(userId, FriendRequest.Status.PENDING)
                .stream().map(FriendDto.FriendRequestResponse::from).toList();
    }

    @Transactional
    public void removeFriend(Long userId, Long otherId) {
        Long a = Math.min(userId, otherId);
        Long b = Math.max(userId, otherId);
        friendRepository.deleteByUserAIdAndUserBId(a, b);
        friendRepository.deleteByUserBIdAndUserAId(a, b);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long a, Long b) {
        return friendRepository.existsByUserAIdAndUserBId(Math.min(a, b), Math.max(a, b));
    }

    public void requireFriends(Long a, Long b) {
        if (!areFriends(a, b)) {
            throw new ConflictException("NOT_FRIENDS", "Only friends can do this");
        }
    }

    private FriendRequest loadForReceiver(Long requestId, Long receiverId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request", requestId));
        if (!request.getReceiver().getId().equals(receiverId)) {
            throw new ConflictException("NOT_YOUR_REQUEST", "Not your request to answer");
        }
        return request;
    }
}
