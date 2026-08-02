package com.sketchtrench.game.repository;

import com.sketchtrench.game.entity.WordCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WordCategoryRepository extends JpaRepository<WordCategory, Long> {

    Optional<WordCategory> findByNameIgnoreCase(String name);

    List<WordCategory> findByActiveTrue();
}
