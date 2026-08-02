package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.Drawing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {

    void deleteByRoundRoomId(Long roomId);

    List<Drawing> findByRoundRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
}
