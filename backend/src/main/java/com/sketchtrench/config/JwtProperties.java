package com.sketchtrench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed view of {@code app.jwt.*} configuration. Using a properties class instead of
 * {@code @Value} everywhere means: one binding point, compile-time field names, and easy
 * unit-testing. Durations bind from ISO strings like {@code 15m} / {@code 7d}.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
