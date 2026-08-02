package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.Guess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuessRepository extends JpaRepository<Guess, Long> {

    void deleteByRoundRoomId(Long roomId);
}
