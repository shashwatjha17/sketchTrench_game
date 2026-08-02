package com.sketchtrench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Flyway migration produces the exact schema we designed, against a REAL
 * PostgreSQL (not an in-memory fake — the whole point is testing the SQL we ship).
 *
 * <p>{@code disabledWithoutDocker = true}: the class silently skips on machines without
 * Docker, so local `mvn test` never fails on a missing daemon. In CI (GitHub Actions,
 * Module 8) Docker exists and this test runs for real.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesAllExpectedTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' " +
                "ORDER BY table_name", String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "roles", "user_roles", "refresh_tokens", "password_reset_tokens",
                "word_categories", "words", "rooms", "room_settings", "room_members",
                "rounds", "drawings", "guesses", "scores", "room_custom_words",
                "achievements", "user_achievements", "seasons", "missions", "user_missions",
                "leaderboards", "game_history", "game_history_players",
                "friend_requests", "friends", "notifications", "reports"
        );
    }

    @Test
    void migrationSeedsDefaultRoles() {
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roles).containsExactly("ADMIN", "PLAYER");
    }
}
