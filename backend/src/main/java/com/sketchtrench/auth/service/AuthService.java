package com.sketchtrench.auth.service;

import com.sketchtrench.auth.dto.AuthResponse;
import com.sketchtrench.auth.dto.LoginRequest;
import com.sketchtrench.auth.security.JwtService;
import com.sketchtrench.common.mail.MailService;
import com.sketchtrench.exception.ConflictException;
import com.sketchtrench.exception.UnauthorizedException;
import com.sketchtrench.user.dto.RegisterRequest;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.RoleRepository;
import com.sketchtrench.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the four auth flows: register, login, logout, and email verification.
 * The {@code AuthenticationManager} handles the actual credential check (it delegates to
 * {@link CustomUserDetailsService} + {@code PasswordEncoder}); this service handles the
 * "what happens on success" business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request, String device, String ip) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("USERNAME_TAKEN", "This username is already taken");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.username());
        user.getRoles().add(roleRepository.findByName("PLAYER")
                .orElseThrow(() -> new IllegalStateException("PLAYER role not seeded")));

        User saved = userRepository.save(user);
        log.info("Registered user '{}' (id={})", saved.getUsername(), saved.getId());

        mailService.sendEmailVerification(saved.getEmail(),
                jwtService.generateEmailVerificationToken(saved.getId(), saved.getEmail()));

        return refreshTokenService.issueTokenPair(saved, device, ip);
    }

    /**
     * {@code authenticationManager.authenticate(...)} throws BadCredentialsException when
     * the password is wrong — the handler maps that to 401. We never reveal WHICH part
     * (email vs password) failed.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String device, String ip) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findWithRolesByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Account not found"));
        return refreshTokenService.issueTokenPair(user, device, ip);
    }

    @Transactional
    public void verifyEmail(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired verification link");
        }
        if (!"email-verification".equals(claims.get("purpose"))) {
            throw new UnauthorizedException("Invalid verification token");
        }
        User user = userRepository.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new UnauthorizedException("Account not found"));
        user.setEmailVerified(true);
        log.info("Email verified for user id={}", user.getId());
    }

    @Transactional(readOnly = true)
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                mailService.sendEmailVerification(user.getEmail(),
                        jwtService.generateEmailVerificationToken(user.getId(), user.getEmail()));
            }
        });
    }
}
