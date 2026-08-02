package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.GameHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {

    @EntityGraph(attributePaths = {"winner", "players", "players.user"})
    List<GameHistory> findByPlayedAtAfterOrderByPlayedAtDesc(Instant after, Pageable pageable);

    long countByPlayedAtAfter(Instant after);

    @EntityGraph(attributePaths = {"winner", "players", "players.user"})
    List<GameHistory> findByPlayersUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);
}
