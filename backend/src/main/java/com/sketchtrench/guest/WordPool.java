package com.sketchtrench.guest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static in-memory word pool. Sessions/games are in-memory anyway; the words are static
 * game content, so a DB-backed store bought nothing. Grow the list if the game feels
 * repetitive.
 */
public final class WordPool {

    private static final List<String> WORDS = List.of(
            "sun", "moon", "cat", "dog", "house", "tree", "star", "heart", "flower",
            "rainbow", "pizza", "book", "car", "hat", "ball", "fish", "bird", "cloud",
            "apple", "banana", "boat", "cake", "castle", "cow", "dragon", "elephant",
            "ghost", "guitar", "key", "ladder", "lamp", "lion", "mountain", "mouse",
            "mushroom", "owl", "penguin", "rocket", "robot", "snowman", "spider",
            "umbrella", "watch", "whale");

    private WordPool() {
    }

    public static List<String> pick(int n) {
        List<String> copy = new ArrayList<>(WORDS);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(n, copy.size()));
    }
}
