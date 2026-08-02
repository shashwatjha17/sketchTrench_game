package com.sketchtrench.auth.service;

import com.sketchtrench.auth.entity.PasswordResetToken;
import com.sketchtrench.auth.repository.PasswordResetTokenRepository;
import com.sketchtrench.auth.security.TokenService;
import com.sketchtrench.common.mail.MailService;
import com.sketchtrench.config.JwtProperties;
import com.sketchtrench.exception.UnauthorizedException;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * "Forgot password" flow. Never reveals whether an email exists (that would enable
 * account enumeration) — the response is identical whether or not the account is real.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final JwtProperties jwtProperties;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            String raw = tokenService.randomToken();
            token.setUser(user);
            token.setTokenHash(tokenService.hash(raw));
            token.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
            tokenRepository.save(token);
            mailService.sendPasswordReset(email, raw);
            log.info("Password reset requested for user id={}", user.getId());
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired reset token");
        }

        token.setUsed(true);
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        // A password change invalidates every session — revoke all refresh tokens.
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password reset for user id={}", user.getId());
    }
}
