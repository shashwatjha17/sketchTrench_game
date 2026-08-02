package com.sketchtrench.auth.controller;

import com.sketchtrench.auth.dto.AuthResponse;
import com.sketchtrench.auth.dto.EmailRequest;
import com.sketchtrench.auth.dto.LoginRequest;
import com.sketchtrench.auth.dto.RefreshTokenRequest;
import com.sketchtrench.auth.dto.ResetPasswordRequest;
import com.sketchtrench.auth.service.AuthService;
import com.sketchtrench.auth.service.PasswordResetService;
import com.sketchtrench.auth.service.RefreshTokenService;
import com.sketchtrench.user.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * All authentication endpoints. Note: controllers carry ZERO business logic — they bind
 * request/response shapes and delegate. {@code device}/{@code ip} are captured for the
 * refresh-token audit trail.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, userAgent(http), clientIp(http)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, userAgent(http), clientIp(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest http) {
        return ResponseEntity.ok(
                refreshTokenService.rotate(request.refreshToken(), userAgent(http), clientIp(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.refreshToken() != null) {
            refreshTokenService.revoke(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        passwordResetService.requestReset(request.email());
        // Always 202 regardless of whether the account exists (anti-enumeration).
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /** GET so the verification link works from a plain email click. */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
