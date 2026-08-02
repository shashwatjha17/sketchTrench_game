package com.sketchtrench.auth.service;

import com.sketchtrench.auth.dto.AuthResponse;
import com.sketchtrench.auth.entity.RefreshToken;
import com.sketchtrench.auth.repository.RefreshTokenRepository;
import com.sketchtrench.auth.security.JwtService;
import com.sketchtrench.auth.security.TokenService;
import com.sketchtrench.config.JwtProperties;
import com.sketchtrench.exception.UnauthorizedException;
import com.sketchtrench.user.dto.UserResponse;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Lifecycle of refresh tokens: issue, rotate on refresh, revoke on logout.
 * "Rotate" is the security crux — each use of a refresh token invalidates it and hands
 * back a new one, so a stolen token is only usable once before being burned.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse issueTokenPair(User user, String device, String ip) {
        List<String> roles = user.getRoles().stream().map(r -> r.getName().toUpperCase()).toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = createRefreshToken(user.getId(), device, ip);
        return new AuthResponse(accessToken, refreshToken,
                jwtService.accessTokenTtlMillis() / 1000, UserResponse.from(user));
    }

    @Transactional
    public AuthResponse rotate(String rawRefreshToken, String device, String ip) {
        RefreshToken stored = findByHash(rawRefreshToken);

        // mark the old token dead BEFORE minting a replacement (all-or-nothing in one tx)
        stored.setRevoked(true);

        User user = userRepository.findWithRolesById(stored.getUser().getId())
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        return issueTokenPair(user, device, ip);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        findByHash(rawRefreshToken).setRevoked(true);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        tokens.forEach(t -> t.setRevoked(true));
    }

    /** One-time storage of a new refresh token; the raw value is returned to the client. */
    private String createRefreshToken(Long userId, String device, String ip) {
        String raw = tokenService.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(userRepository.getReferenceById(userId));
        token.setTokenHash(tokenService.hash(raw));
        token.setDevice(device);
        token.setIpAddress(ip);
        token.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(token);
        return raw;
    }

    private RefreshToken findByHash(String raw) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenService.hash(raw))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.isRevoked() || stored.isExpired()) {
            throw new UnauthorizedException("Refresh token is no longer valid");
        }
        return stored;
    }
}
