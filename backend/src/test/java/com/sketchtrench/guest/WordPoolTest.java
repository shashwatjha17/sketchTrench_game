package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordPoolTest {

    @Test
    void everyLanguageHasABigPool() {
        for (String lang : WordPool.LANGUAGES) {
            assertTrue(WordPool.words(lang).size() >= 200, lang);
        }
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertEquals("en", WordPool.normalizeLanguage("xx"));
        assertEquals("en", WordPool.normalizeLanguage(null));
        assertEquals(WordPool.words("en"), WordPool.words("klingon"));
    }

    @Test
    void pickAvoidsExcludedWords() {
        List<String> all = WordPool.words("fr");
        Set<String> used = new HashSet<>(all.subList(0, all.size() - 3));
        List<String> picked = WordPool.pick("fr", 3, used);
        assertEquals(3, picked.size());
        picked.forEach(w -> assertFalse(used.contains(w), w));
    }

    @Test
    void pickAllowsRepeatsOnceThePoolRunsOut() {
        List<String> all = WordPool.words("de");
        assertEquals(3, WordPool.pick("de", 3, new HashSet<>(all)).size());
    }
}
