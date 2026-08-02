package com.sketchtrench.progress.service;

import com.sketchtrench.game.entity.GameHistory;
import com.sketchtrench.game.entity.GameHistoryPlayer;
import com.sketchtrench.game.repository.GameHistoryPlayerRepository;
import com.sketchtrench.game.repository.GameHistoryRepository;
import com.sketchtrench.progress.dto.ProfileDto;
import com.sketchtrench.progress.entity.UserAchievement;
import com.sketchtrench.progress.repository.UserAchievementRepository;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side aggregation for the profile page: personal stats, match history, and
 * achievements. Pure queries — no writes live here.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final GameHistoryPlayerRepository gameHistoryPlayerRepository;
    private final UserAchievementRepository userAchievementRepository;

    @Transactional(readOnly = true)
    public ProfileDto.Stats stats(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        long games = gameHistoryPlayerRepository.countByUserId(userId);
        long wins = gameHistoryPlayerRepository.countByUserIdAndPosition(userId, 1);
        long points = gameHistoryPlayerRepository.sumPointsByUserId(userId);
        return new ProfileDto.Stats(
                games, wins, points,
                games == 0 ? 0 : Math.round((100.0 * wins) / games),
                games == 0 ? 0 : points / games,
                user.getXp(), user.getLevel(), user.getEloRating(), user.getLeague());
    }

    @Transactional(readOnly = true)
    public List<ProfileDto.MatchHistory> history(Long userId, int limit) {
        return gameHistoryRepository
                .findByPlayersUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, Math.min(limit, 50)))
                .stream().map(game -> toMatchHistory(game, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileDto.AchievementProgress> achievements(Long userId) {
        return userAchievementRepository.findByUserId(userId).stream()
                .map(ua -> new ProfileDto.AchievementProgress(ua.getAchievement().getCode(),
                        ua.getAchievement().getName(), ua.getAchievement().getDescription(),
                        ua.getProgress(), ua.getUnlockedAt()))
                .toList();
    }

    private ProfileDto.MatchHistory toMatchHistory(GameHistory game, Long userId) {
        GameHistoryPlayer mine = game.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(userId)).findFirst().orElse(null);
        return new ProfileDto.MatchHistory(game.getId(), game.getMode(), game.getPlayedAt(),
                mine == null ? 0 : mine.getPosition(), mine == null ? 0 : mine.getPoints(),
                game.getWinner() == null ? null : game.getWinner().getUsername());
    }
}
