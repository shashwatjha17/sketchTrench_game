package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    void deleteByRoomId(Long roomId);
}
