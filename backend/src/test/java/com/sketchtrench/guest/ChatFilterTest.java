package com.sketchtrench.guest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterTest {

    @Test
    void censorsBlockedWordsKeepingLength() {
        assertEquals("what the **** is that", ChatFilter.censor("what the fuck is that"));
        assertEquals("****!", ChatFilter.censor("shit!"));
    }

    @Test
    void catchesLeetspeakStretchingAndAccents() {
        assertTrue(ChatFilter.containsProfanity("sh1t"));
        assertTrue(ChatFilter.containsProfanity("FUUUUCK"));
        assertTrue(ChatFilter.containsProfanity("f*ck you") || ChatFilter.containsProfanity("fucking"));
        assertTrue(ChatFilter.containsProfanity("bullshit"));
        assertTrue(ChatFilter.containsProfanity("enculé"));
    }

    @Test
    void catchesRootsHiddenInRunTogetherNames() {
        assertTrue(ChatFilter.containsProfanity("xXfuckXx"));
        assertTrue(ChatFilter.containsProfanity("Big_Shit_99"));
    }

    @Test
    void leavesOrdinaryWordsAlone() {
        for (String ok : new String[]{"as", "class", "assess", "pass the ball", "Dickens", "Scunthorpe",
                "Nigeria", "Niger", "cucumber", "grass", "basement", "cocktail"}) {
            assertFalse(ChatFilter.containsProfanity(ok), ok);
            assertEquals(ok, ChatFilter.censor(ok), ok);
        }
    }

    @Test
    void gameWordsAreNeverBlocked() {
        for (String lang : WordPool.LANGUAGES) {
            for (String w : WordPool.words(lang)) {
                assertFalse(ChatFilter.containsProfanity(w), lang + ": " + w);
            }
        }
    }
}
