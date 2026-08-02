package com.sketchtrench.auth.dto;

import com.sketchtrench.user.dto.UserResponse;

/**
 * The auth contract: short-lived access token + long-lived refresh token + the user.
 * Access token TTL is included so the client can schedule proactive refreshes.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserResponse user
) {
}
