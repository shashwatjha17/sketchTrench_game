package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.RoomCustomWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomCustomWordRepository extends JpaRepository<RoomCustomWord, Long> {

    List<RoomCustomWord> findByRoomIdOrderByIdAsc(Long roomId);

    Optional<RoomCustomWord> findByRoomIdAndWord(Long roomId, String word);

    long countByRoomId(Long roomId);

    void deleteByIdAndRoomId(Long id, Long roomId);
}
