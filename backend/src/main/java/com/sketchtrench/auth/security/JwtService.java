package com.sketchtrench.auth.security;

import com.sketchtrench.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Creates and verifies JWT access tokens (jjwt 0.12 API).
 *
 * <p>Why JWT for access tokens? Stateless — the auth server writes a signed token and
 * every node of the fleet can verify it with just the shared secret, no session store,
 * no DB hit per request. The refresh token is the stateful counterpart (DB-backed) that
 * lets us revoke sessions, which JWTs alone cannot do.
 *
 * <p>Claims: {@code sub}=userId, {@code username}, {@code roles}. Roles live in the token
 * so authorization is decided without a DB lookup on every request.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMillis;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = properties.accessTokenTtl().toMillis();
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTtlMillis)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies signature + expiry. Throws {@link io.jsonwebtoken.JwtException}
     * on any tampering, which the filter catches and treats as "not authenticated".
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTokenTtlMillis() {
        return accessTtlMillis;
    }

    /** Short-lived token (24h) proving email ownership; the verify-email link carries it. */
    public String generateEmailVerificationToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("purpose", "email-verification")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(86_400)))
                .signWith(key)
                .compact();
    }
}
