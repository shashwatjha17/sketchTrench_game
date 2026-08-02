package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.GameHistoryPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameHistoryPlayerRepository extends JpaRepository<GameHistoryPlayer, GameHistoryPlayer.GameHistoryPlayerId> {

    long countByUserId(Long userId);

    long countByUserIdAndPosition(Long userId, int position);

    @Query("SELECT COALESCE(SUM(p.points), 0) FROM GameHistoryPlayer p WHERE p.user.id = :userId")
    long sumPointsByUserId(Long userId);
}
