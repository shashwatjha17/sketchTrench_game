package com.sketchtrench.progress.controller;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.progress.dto.LeaderboardDto;
import com.sketchtrench.progress.service.LeaderboardService;
import com.sketchtrench.user.entity.League;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public List<LeaderboardDto.Entry> global(@RequestParam(required = false) League league,
                                             @RequestParam(defaultValue = "50") int limit) {
        return leaderboardService.global(league, limit);
    }

    @GetMapping("/friends")
    public List<LeaderboardDto.Entry> friends(@RequestParam(defaultValue = "50") int limit) {
        return leaderboardService.friends(SecurityUtils.currentUserId(), limit);
    }
}
