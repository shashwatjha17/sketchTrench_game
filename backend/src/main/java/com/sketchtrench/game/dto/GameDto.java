package com.sketchtrench.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Inbound requests + outbound broadcasts for the game. One file keeps the contract visible. */
public record GameDto() {

    // ---- inbound ----
    public record GuessRequest(@NotBlank @Size(max = 64) String text) {
    }

    public record WordChoiceRequest(@NotNull Long wordId) {
    }

    public record ChatRequest(@NotBlank @Size(max = 200) String text) {
    }

    // ---- outbound ----
    public record GameStarted(int totalRounds, List<Long> playerOrder, Map<Long, String> players) {
    }

    public record WordOptions(int roundNumber, List<WordOption> options) {
    }

    public record WordOption(Long id, String text) {
    }

    /** Sent ONLY to the drawer (private /user/{name}/queue/word). */
    public record SecretWord(String word) {
    }

    public record RoundStarting(int roundNumber, Long drawerId, String drawerName, int optionCount) {
    }

    public record RoundStarted(int roundNumber, Long drawerId, String drawerName, int drawingTimeSec) {
    }

    public record TimerUpdate(int remainingSeconds) {
    }

    public record CorrectGuess(Long userId, String username, int points, int remainingGuessers) {
    }

    public record RoundEnded(int roundNumber, String word, Long drawerId, int drawerBonus,
                             Map<Long, Integer> scores) {
    }

    public record GameEnded(Long winnerId, String winnerName, Map<Long, Integer> scores) {
    }

    public record ChatMessage(Long userId, String username, String text, Instant sentAt) {
    }

    public record TypingIndicator(Long userId, String username) {
    }

    /** Snapshot of a live game — lets a client that refreshed mid-game rejoin. */
    public record GameState(int roundNumber, Long drawerId, String drawerName, int drawingTimeSec,
                            int remainingSeconds, Map<Long, Integer> scores, String secretWord,
                            List<Object> strokes, boolean active) {
    }
}
