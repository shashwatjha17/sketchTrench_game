package com.sketchtrench.room.repository;

import com.sketchtrench.room.entity.Room;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @EntityGraph(attributePaths = {"host", "settings", "members", "members.user"})
    Optional<Room> findWithDetailsById(Long id);

    List<Room> findByStatusAndVisibilityOrderByCreatedAtDesc(Room.Status status, Room.Visibility visibility);

    Optional<Room> findByInviteCode(String inviteCode);

    List<Room> findByStatusAndNameContainingIgnoreCase(Room.Status status, String query);
}
