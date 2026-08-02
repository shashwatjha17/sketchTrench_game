package com.sketchtrench.progress.service;

import com.sketchtrench.progress.dto.LeaderboardDto;
import com.sketchtrench.social.entity.Friend;
import com.sketchtrench.social.repository.FriendRepository;
import com.sketchtrench.user.entity.League;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Ranking queries. Global = by ELO; friends = friends sorted by ELO. */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;

    @Transactional(readOnly = true)
    public List<LeaderboardDto.Entry> global(League league, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        List<User> users = league == null
                ? userRepository.findByOrderByEloRatingDesc(page)
                : userRepository.findByLeagueOrderByEloRatingDesc(league, page);
        return users.stream().map(LeaderboardDto.Entry::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardDto.Entry> friends(Long userId, int limit) {
        List<Friend> edges = friendRepository.findByUserAIdOrUserBId(userId, userId);
        return edges.stream()
                .map(edge -> edge.getUserA().getId().equals(userId) ? edge.getUserB() : edge.getUserA())
                .sorted(Comparator.comparingInt(User::getEloRating).reversed())
                .limit(Math.min(limit, 100))
                .map(LeaderboardDto.Entry::from)
                .toList();
    }
}
