package com.sketchtrench.progress.service;

import com.sketchtrench.game.entity.GameHistory;
import com.sketchtrench.game.entity.GameHistoryPlayer;
import com.sketchtrench.game.repository.GameHistoryRepository;
import com.sketchtrench.progress.ProgressTracker;
import com.sketchtrench.progress.entity.Achievement;
import com.sketchtrench.progress.entity.UserAchievement;
import com.sketchtrench.progress.repository.AchievementRepository;
import com.sketchtrench.progress.repository.UserAchievementRepository;
import com.sketchtrench.room.repository.RoomRepository;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns game events into progression: XP + levels, ELO ratings, achievements, and the
 * match-history ledger. Consumed by the game engine through {@link ProgressTracker} — the
 * engine never needs to know any of this exists.
 *
 * <p>XP and ELO are deliberately separate currencies: XP measures participation (levels
 * feel rewarding, always go up), ELO measures skill (ranked, goes both ways).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressionService implements ProgressTracker {

    /** How much progress an achievement needs before it unlocks. One-shot = 1. */
    private static final Map<String, Integer> ACHIEVEMENT_TARGETS = Map.of(
            "FIRST_GAME", 1, "FIRST_GUESS", 1, "QUICK_GUESS", 1, "WINNER", 1,
            "LEVEL_5", 1, "CORRECT_10", 10);

    private static final int XP_PER_GUESS = 20;
    private static final int XP_PARTICIPATION = 50;
    private static final int XP_WIN = 100;
    private static final int ELO_K = 32;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final GameHistoryRepository gameHistoryRepository;

    // ========================================================================
    // ProgressTracker implementation (called by the game engine)
    // ========================================================================

    @Override
    @Transactional
    public void onCorrectGuess(Long userId, int remainingSeconds) {
        addXp(userId, XP_PER_GUESS + remainingSeconds);
        progressAchievement(userId, "FIRST_GUESS");
        progressAchievement(userId, "CORRECT_10");
        if (remainingSeconds <= 10) {
            progressAchievement(userId, "QUICK_GUESS");
        }
    }

    @Override
    @Transactional
    public void onGameFinished(GameSummary summary) {
        List<Long> playerIds = summary.results().stream().map(PlayerResult::userId).toList();

        // participation XP + FIRST_GAME for everyone
        playerIds.forEach(userId -> {
            addXp(userId, XP_PARTICIPATION);
            progressAchievement(userId, "FIRST_GAME");
        });
        // winner bonus
        if (summary.winnerId() != null) {
            addXp(summary.winnerId(), XP_WIN);
            progressAchievement(summary.winnerId(), "WINNER");
        }

        // ranked games adjust ELO
        Map<Long, Integer> newRatings = Map.of();
        if ("RANKED".equals(summary.mode())) {
            newRatings = recomputeElo(summary.results());
            newRatings.forEach((userId, rating) ->
                    userRepository.findById(userId).ifPresent(user -> user.setEloRating(rating)));
        }

        recordGameHistory(summary, newRatings);
        log.info("Progression recorded for game in room {} ({} players)", summary.roomId(), playerIds.size());
    }

    /** Called by the social module when a friendship is confirmed. */
    @Transactional
    public void onFriendAdded(Long userId) {
        progressAchievement(userId, "FRIEND_1");
    }

    // ========================================================================
    // Match history + stats
    // ========================================================================

    @Transactional
    public void recordGameHistory(GameSummary summary, Map<Long, Integer> newRatings) {
        GameHistory game = new GameHistory();
        game.setRoom(roomRepository.getReferenceById(summary.roomId()));
        if (summary.winnerId() != null) {
            game.setWinner(userRepository.getReferenceById(summary.winnerId()));
        }
        game.setMode(summary.mode());
        game.setDurationSec(summary.durationSec());
        game.setPlayedAt(Instant.now());

        for (PlayerResult result : summary.results()) {
            GameHistoryPlayer player = new GameHistoryPlayer();
            player.setGame(game);
            player.setUser(userRepository.getReferenceById(result.userId()));
            player.setPosition(result.position());
            player.setPoints(result.points());
            player.setEloDelta(newRatings.getOrDefault(result.userId(), result.eloBefore()) - result.eloBefore());
            game.getPlayers().add(player);
        }
        gameHistoryRepository.save(game);
    }

    // ========================================================================
    // Internals
    // ========================================================================

    @Transactional
    public void addXp(Long userId, int amount) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setXp(user.getXp() + amount);
        int newLevel = levelForXp(user.getXp());
        if (newLevel > user.getLevel()) {
            user.setLevel(newLevel);
            log.info("User {} leveled up to {}", userId, newLevel);
        }
        if (user.getLevel() >= 5) {
            progressAchievement(userId, "LEVEL_5");
        }
    }

    private void progressAchievement(Long userId, String code) {
        int target = ACHIEVEMENT_TARGETS.getOrDefault(code, 1);
        Achievement achievement = achievementRepository.findByCode(code).orElse(null);
        if (achievement == null) {
            return;
        }
        UserAchievement userAchievement = userAchievementRepository
                .findByUserIdAndAchievementCode(userId, code)
                .orElseGet(() -> {
                    UserAchievement ua = new UserAchievement();
                    ua.setUser(userRepository.getReferenceById(userId));
                    ua.setAchievement(achievement);
                    return ua;
                });

        if (userAchievement.getUnlockedAt() != null) {
            return; // already earned
        }
        userAchievement.setProgress(userAchievement.getProgress() + 1);
        if (userAchievement.getProgress() >= target) {
            userAchievement.setUnlockedAt(Instant.now());
            userAchievementRepository.save(userAchievement);
            addXp(userId, achievement.getXpReward());
            log.info("Achievement '{}' unlocked for user {}", code, userId);
        } else {
            userAchievementRepository.save(userAchievement);
        }
    }

    /**
     * Multiplayer ELO: each player's expected score is averaged pairwise against every
     * opponent (400-scale logistic), their actual score is their finish share, and the
     * delta is K * (actual - expected). Standard, explainable, clamp at 100.
     */
    static Map<Long, Integer> recomputeElo(List<PlayerResult> results) {
        Map<Long, Integer> updated = new java.util.HashMap<>();
        int n = results.size();
        if (n < 2) {
            return updated;
        }
        for (PlayerResult current : results) {
            double expected = 0;
            for (PlayerResult opponent : results) {
                if (opponent.userId().equals(current.userId())) {
                    continue;
                }
                expected += 1.0 / (1 + Math.pow(10, (opponent.eloBefore() - current.eloBefore()) / 400.0));
            }
            expected /= (n - 1);

            double actual = 1.0 * (n - current.position()) / Math.max(n - 1, 1);
            int delta = (int) Math.round(ELO_K * (actual - expected));
            int rating = Math.max(100, current.eloBefore() + delta);
            updated.put(current.userId(), rating);
        }
        return updated;
    }

    static int levelForXp(int xp) {
        return (int) Math.sqrt(xp / 100.0) + 1;
    }
}
