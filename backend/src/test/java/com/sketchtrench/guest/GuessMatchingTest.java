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
}
