package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    List<Word> findByActiveTrueAndCategoryId(Long categoryId);

    List<Word> findByActiveTrue();

    Optional<Word> findByTextIgnoreCase(String text);

    long countByActiveTrue();

    /** The n least-used active words in a category — freshness-aware word picking. */
    @Query("""
            SELECT w FROM Word w
            WHERE w.active = true AND w.category.id = :categoryId
            ORDER BY w.timesUsed ASC, w.id ASC
            LIMIT :limit
            """)
    List<Word> findFreshestByCategory(Long categoryId, int limit);

    @Modifying
    @Query("UPDATE Word w SET w.timesUsed = w.timesUsed + 1 WHERE w.id IN :ids")
    void incrementTimesUsed(List<Long> ids);
}
