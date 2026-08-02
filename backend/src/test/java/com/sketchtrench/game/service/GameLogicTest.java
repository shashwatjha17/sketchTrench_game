package com.sketchtrench.game.service;

import org.junit.jupiter.api.Test;

import static com.sketchtrench.game.service.GameService.ChatFilter;
import static org.assertj.core.api.Assertions.assertThat;

class GameLogicTest {

    @Test
    void normalizeStripsCasePunctuationAndCollapsesSpaces() {
        assertThat(GameService.normalize("  Hello,  WORLD!! ")).isEqualTo("hello world");
        assertThat(GameService.normalize("The Rocket   🚀")).isEqualTo("the rocket");
        assertThat(GameService.normalize("café")).isEqualTo("caf");
    }

    @Test
    void levenshteinMeasuresEditDistance() {
        assertThat(GameService.levenshtein("kitten", "sitting")).isEqualTo(3);
        assertThat(GameService.levenshtein("cat", "cat")).isZero();
        assertThat(GameService.levenshtein("", "abc")).isEqualTo(3);
    }

    @Test
    void lenientGuessAcceptsOneTypo() {
        String answer = GameService.normalize("cat");
        // insertion ("cats"), substitution ("cut"), insertion ("cart") are all distance 1 -> tolerated
        assertThat(GameService.levenshtein(GameService.normalize("cats"), answer)).isEqualTo(1);
        assertThat(GameService.levenshtein(GameService.normalize("cut"), answer)).isEqualTo(1);
        assertThat(GameService.levenshtein(GameService.normalize("cart"), answer)).isEqualTo(1);
        // two edits ("crate") -> rejected
        assertThat(GameService.levenshtein(GameService.normalize("crate"), answer)).isEqualTo(2);
    }

    @Test
    void chatFilterCensorsBannedWordsCaseInsensitively() {
        assertThat(ChatFilter.sanitize("fuck you")).isEqualTo("**** you");
        assertThat(ChatFilter.sanitize("Shit happens")).isEqualTo("**** happens");
        assertThat(ChatFilter.sanitize("no bad words here")).isEqualTo("no bad words here");
        // derived forms ("fucking") intentionally slip past the boundary-aware blocklist
        assertThat(ChatFilter.sanitize("this is fucking great")).isEqualTo("this is fucking great");
    }
}
