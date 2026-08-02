package com.sketchtrench.social.repository;

import com.sketchtrench.social.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByReceiverIdAndStatus(Long receiverId, FriendRequest.Status status);

    List<FriendRequest> findBySenderIdAndStatus(Long senderId, FriendRequest.Status status);

    boolean existsBySenderIdAndReceiverIdAndStatus(Long senderId, Long receiverId,
                                                   FriendRequest.Status status);

    Optional<FriendRequest> findBySenderIdAndReceiverId(Long senderId, Long receiverId);
}
