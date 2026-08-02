package com.sketchtrench.progress;

import java.util.List;

/**
 * Hook the game engine calls so progression features (XP, levels, ELO, achievements,
 * match history) stay decoupled from the round loop. GameService knows NOTHING about
 * how progression is computed — it only reports facts. Implemented in the progress module.
 */
public interface ProgressTracker {

    void onCorrectGuess(Long userId, int remainingSeconds);

    void onGameFinished(GameSummary summary);

    record GameSummary(Long roomId, String mode, Long winnerId, int durationSec,
                       List<PlayerResult> results) {
    }

    record PlayerResult(Long userId, int position, int points, int eloBefore) {
    }
}
