package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {

    void deleteByRoomId(Long roomId);
}
