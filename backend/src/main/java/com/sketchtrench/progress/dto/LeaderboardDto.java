package com.sketchtrench.progress.dto;

import com.sketchtrench.user.entity.League;
import com.sketchtrench.user.entity.User;

import java.util.List;

public record LeaderboardDto() {

    public record Entry(
            Long userId,
            String username,
            int eloRating,
            int level,
            League league
    ) {
        public static Entry from(User user) {
            return new Entry(user.getId(), user.getUsername(), user.getEloRating(),
                    user.getLevel(), user.getLeague());
        }
    }

    public record Board(String title, List<Entry> entries) {
    }
}
