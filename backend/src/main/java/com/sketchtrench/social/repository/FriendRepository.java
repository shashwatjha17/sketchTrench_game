package com.sketchtrench.social.repository;

import com.sketchtrench.social.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendRepository extends JpaRepository<Friend, Friend.FriendId> {

    List<Friend> findByUserAIdOrUserBId(Long a, Long b);

    boolean existsByUserAIdAndUserBId(Long a, Long b);

    void deleteByUserAIdAndUserBId(Long a, Long b);

    void deleteByUserBIdAndUserAId(Long a, Long b);
}
