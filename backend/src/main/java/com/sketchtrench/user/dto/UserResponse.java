package com.sketchtrench.user.dto;

import com.sketchtrench.user.entity.User;

import java.time.Instant;

/**
 * Public view of a user. Never return the {@link User} entity from an API — it would
 * leak the BCrypt password hash and couples the DB schema to the HTTP contract.
 * A record gives us immutability + value equality for free.
 */
public record UserResponse(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String avatarColor,
        String avatarExpression,
        boolean avatarSunglasses,
        String avatarWig,
        int xp,
        int level,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getAvatarColor(),
                user.getAvatarExpression(),
                user.isAvatarSunglasses(),
                user.getAvatarWig(),
                user.getXp(),
                user.getLevel(),
                user.getCreatedAt()
        );
    }
}
