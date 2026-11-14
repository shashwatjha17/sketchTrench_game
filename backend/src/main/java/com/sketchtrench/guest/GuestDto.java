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
                                    List<String> customWordList, String language, Boolean hints) {
    }

    /** Host changes room settings before the game starts. Null fields stay as they are. */
    public record SettingsRequest(Boolean isPrivate, Integer maxPlayers, Integer totalRounds,
                                  Integer drawingTimeSec, Boolean customWords, String language,
                                  Boolean hints) {
    }

    public record JoinRequest(String password) {
    }

    public record WordRequest(List<String> words) {
    }

    // ---- player / room snapshots ----
    public record PlayerInfo(String playerId, String nickname, String avatarColor, String avatarExpression,
                             boolean avatarSunglasses, String avatarWig, String language,
                             int score, boolean isHost, boolean isDrawing, boolean isConnected,
                             boolean isBot) {
    }

    public record RoomInfo(String roomId, String name, boolean isPrivate, String status, String hostId,
                           int maxPlayers, int totalRounds, int drawingTimeSec, boolean customWordsEnabled,
                           int currentRound, int playerCount, List<String> customWords,
                           List<PlayerInfo> players, String language, boolean hintsEnabled) {
    }

    /**
     * Snapshot for a (re)joining client. {@code wordOptions} is only filled for the drawer while
     * they still have to pick (so a drawer who missed the private push can still choose);
     * {@code wordPattern} is the word with every letter blanked ("_____ ___").
     */
    public record GameState(int roundNumber, int totalRounds, String drawerId, String drawerName, int drawingTimeSec,
                            int remainingSeconds, Map<String, Integer> scores, String secretWord,
                            List<Object> strokes, boolean active, boolean choosing,
                            List<WordOption> wordOptions, String wordPattern) {
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

    public record RoundStarted(int roundNumber, String drawerId, String drawerName, int drawingTimeSec,
                               String wordPattern) {
    }

    public record TimerUpdate(int remainingSeconds) {
    }

    /** A letter of the word was revealed to guessers: the updated blanked pattern ("_ a _"). */
    public record WordHint(String wordPattern) {
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

    /** Private nudge to a guesser whose guess was one or two letters off. */
    public record CloseGuess(String closeGuess) {
    }

    /** Server notice shown in the chat (votes, kicks, blocked messages). */
    public record SystemMessage(String system) {
    }

    /** Sent privately to a player who was removed from the room. */
    public record Kicked(String kickedFrom) {
    }
}
