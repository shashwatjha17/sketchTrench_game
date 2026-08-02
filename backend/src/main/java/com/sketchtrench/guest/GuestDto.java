package com.sketchtrench.guest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Inbound requests + outbound broadcasts for the guest-only flow. One file keeps the contract visible. */
public record GuestDto() {

    // ---- inbound ----
    public record SessionRequest(String nickname, String avatarColor, String avatarExpression,
                                 boolean avatarSunglasses, String avatarWig, String language) {
    }

    public record ReconnectRequest(String playerId, String reconnectToken) {
    }

    public record CreateRoomRequest(String name, boolean isPrivate, int maxPlayers,
                                    int totalRounds, int drawingTimeSec, boolean customWords,
                                    List<String> customWordList) {
    }

    public record JoinRequest(String password) {
    }

    public record WordRequest(List<String> words) {
    }

    // ---- player / room snapshots ----
    public record PlayerInfo(String playerId, String nickname, String avatarColor, String avatarExpression,
                             boolean avatarSunglasses, String avatarWig, String language,
                             int score, boolean isHost, boolean isDrawing, boolean isConnected) {
    }

    public record RoomInfo(String roomId, String name, boolean isPrivate, String status, String hostId,
                           int maxPlayers, int totalRounds, int drawingTimeSec, boolean customWordsEnabled,
                           int currentRound, int playerCount, List<String> customWords,
                           List<PlayerInfo> players) {
    }

    public record GameState(int roundNumber, String drawerId, String drawerName, int drawingTimeSec,
                            int remainingSeconds, Map<String, Integer> scores, String secretWord,
                            List<Object> strokes, boolean active) {
    }

    // ---- game broadcasts (mirror the old GameDto so the frontend keeps its shapes) ----
    public record WordOption(String id, String text) {
    }

    public record WordOptions(int roundNumber, List<WordOption> options) {
    }

    public record SecretWord(String word) {
    }

    public record RoundStarting(int roundNumber, String drawerId, String drawerName, int optionCount) {
    }

    public record RoundStarted(int roundNumber, String drawerId, String drawerName, int drawingTimeSec) {
    }

    public record TimerUpdate(int remainingSeconds) {
    }

    public record CorrectGuess(String userId, String username, int points, int remainingGuessers) {
    }

    public record RoundEnded(int roundNumber, String word, String drawerId, int drawerBonus,
                             Map<String, Integer> scores) {
    }

    public record GameEnded(String winnerId, String winnerName, Map<String, Integer> scores) {
    }

    public record ChatMessage(String userId, String username, String text, Instant sentAt) {
    }

    public record TypingIndicator(String userId, String username) {
    }
}
