package com.sketchtrench.user.service;

import com.sketchtrench.common.security.SecurityUtils;
import com.sketchtrench.exception.NotFoundException;
import com.sketchtrench.user.dto.ProfileUpdateRequest;
import com.sketchtrench.user.dto.UserResponse;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side profile operations. Registration lives in the auth module (it also issues
 * tokens); this service is purely about viewing users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User user = userRepository.findWithRolesByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new NotFoundException("User", SecurityUtils.currentUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(ProfileUpdateRequest request) {
        User user = userRepository.findWithRolesByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new NotFoundException("User", SecurityUtils.currentUsername()));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.color() != null) {
            user.setAvatarColor(request.color());
        }
        if (request.expression() != null) {
            user.setAvatarExpression(request.expression());
        }
        if (request.sunglasses() != null) {
            user.setAvatarSunglasses(request.sunglasses());
        }
        if (request.wig() != null) {
            user.setAvatarWig(request.wig());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    /** Username-prefix search, capped — used by "add friend" and invites. */
    @Transactional(readOnly = true)
    public List<UserResponse> search(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query).stream()
                .filter(u -> !u.getId().equals(SecurityUtils.currentUserId()))
                .map(UserResponse::from)
                .toList();
    }
}
