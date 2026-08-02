package com.sketchtrench.progress.dto;

import com.sketchtrench.user.entity.League;

import java.time.Instant;

public record ProfileDto() {

    public record Stats(
            long gamesPlayed,
            long wins,
            long totalPoints,
            long winRatePercent,
            long avgPointsPerGame,
            int xp,
            int level,
            int eloRating,
            League league
    ) {
    }

    public record MatchHistory(
            Long gameId,
            String mode,
            Instant playedAt,
            int position,
            int points,
            String winner
    ) {
    }

    public record AchievementProgress(
            String code,
            String name,
            String description,
            int progress,
            Instant unlockedAt
    ) {
    }
}
