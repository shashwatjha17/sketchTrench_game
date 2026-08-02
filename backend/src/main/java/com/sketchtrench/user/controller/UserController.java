package com.sketchtrench.user.controller;

import com.sketchtrench.progress.dto.ProfileDto;
import com.sketchtrench.progress.service.StatsService;
import com.sketchtrench.user.dto.ProfileUpdateRequest;
import com.sketchtrench.user.dto.UserResponse;
import com.sketchtrench.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public + self profile endpoints. Registration/login live under /api/auth.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StatsService statsService;

    @GetMapping("/me")
    public UserResponse me() {
        return userService.getMe();
    }

    @PutMapping("/me/profile")
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(request);
    }

    @GetMapping("/{id}")
    public UserResponse profile(@PathVariable Long id) {
        return userService.getProfile(id);
    }

    @GetMapping("/search")
    public List<UserResponse> search(@RequestParam String q) {
        return userService.search(q);
    }

    @GetMapping("/{id}/stats")
    public ProfileDto.Stats stats(@PathVariable Long id) {
        return statsService.stats(id);
    }

    @GetMapping("/{id}/history")
    public List<ProfileDto.MatchHistory> history(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return statsService.history(id, limit);
    }

    @GetMapping("/{id}/achievements")
    public List<ProfileDto.AchievementProgress> achievements(@PathVariable Long id) {
        return statsService.achievements(id);
    }
}
