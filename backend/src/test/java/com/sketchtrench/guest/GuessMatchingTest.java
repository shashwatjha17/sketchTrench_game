package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for guess normalisation and the "close guess" hint. */
class GuessMatchingTest {

    @Test
    void normalizeIgnoresCaseAndExtraSpaces() {
        assertEquals("ice cream", GuestService.normalize("  Ice   CREAM "));
    }

    @Test
    void oneTypoIsClose() {
        assertTrue(GuestService.isClose("elephnt", "elephant"));   // missing letter
        assertTrue(GuestService.isClose("rockte", "rocket"));      // swapped letters
        assertTrue(GuestService.isClose("guitat", "guitar"));      // wrong letter
    }

    @Test
    void exactOrFarGuessesAreNotClose() {
        assertFalse(GuestService.isClose("rocket", "rocket"));
        assertFalse(GuestService.isClose("dog", "elephant"));
        assertFalse(GuestService.isClose("", "rocket"));
    }

    @Test
    void longWordsAllowTwoTypos() {
        assertTrue(GuestService.isClose("elefant", "elephant"));
        assertFalse(GuestService.isClose("rokit", "rocket")); // 2 typos on a 6-letter word
    }

    @Test
    void swapCountsAsOneEdit() {
        assertEquals(1, GuestService.levenshtein("ab", "ba"));
        assertEquals(3, GuestService.levenshtein("kitten", "sitting"));
    }

    @Test
    void patternBlanksLettersButKeepsSpaces() {
        assertEquals("___ _____", GuestService.pattern("ice cream"));
        assertEquals("_-_____", GuestService.pattern("t-shirt"));
        assertEquals(null, GuestService.pattern(null));
    }

    @Test
    void guessesIgnoreAccentsAndHyphens() {
        assertEquals(GuestService.normalize("éléphant"), GuestService.normalize("Elephant"));
        assertEquals(GuestService.normalize("chauve-souris"), GuestService.normalize("chauve souris"));
    }

    @Test
    void patternShowsRevealedLetters() {
        assertEquals("_a__", GuestService.pattern("lamp", java.util.Set.of(1)));
        assertEquals("i__ ____m", GuestService.pattern("ice cream", java.util.Set.of(0, 8)));
    }

    @Test
    void hintCountScalesWithWordLengthAndKeepsTwoHidden() {
        assertEquals(0, GuestService.hintCount("ox"));
        assertEquals(1, GuestService.hintCount("cat"));
        assertEquals(1, GuestService.hintCount("lamp"));
        assertEquals(2, GuestService.hintCount("tiger"));
        assertEquals(3, GuestService.hintCount("elephant"));
        assertEquals(4, GuestService.hintCount("cepillo de dientes"));
    }

    @Test
    void settingsAreClamped() {
        assertEquals(1, GuestService.clamp(0, 1, 10));
        assertEquals(10, GuestService.clamp(99, 1, 10));
        assertEquals(5, GuestService.clamp(5, 1, 10));
    }
}
