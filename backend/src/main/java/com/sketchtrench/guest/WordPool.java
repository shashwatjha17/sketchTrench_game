package com.sketchtrench.guest;

import com.sketchtrench.game.entity.Word;
import com.sketchtrench.game.repository.WordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The word pool lives in memory only. It is seeded once from the words table at startup
 * (those seeds are static game content, not player data) and never touches the DB again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordPool {

    private static final List<String> FALLBACK = List.of(
            "sun", "moon", "cat", "dog", "house", "tree", "star", "heart", "flower",
            "rainbow", "pizza", "book", "car", "hat", "ball", "fish", "bird", "cloud");

    private final WordRepository wordRepository;
    private volatile List<String> words = FALLBACK;

    @PostConstruct
    void load() {
        try {
            List<String> loaded = wordRepository.findByActiveTrue().stream()
                    .map(Word::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
            if (!loaded.isEmpty()) {
                words = loaded;
            }
            log.info("Word pool seeded with {} words", words.size());
        } catch (Exception e) {
            log.warn("Could not load words from DB, using built-in fallback: {}", e.getMessage());
        }
    }

    public List<String> pick(int n) {
        List<String> copy = new ArrayList<>(words);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(n, copy.size()));
    }
}
