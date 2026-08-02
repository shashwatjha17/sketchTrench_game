package com.sketchtrench.game;

import com.sketchtrench.game.dto.GameDto;
import com.sketchtrench.game.service.GameService;
import com.sketchtrench.room.dto.RoomDto;
import com.sketchtrench.room.service.RoomService;
import com.sketchtrench.user.entity.Role;
import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.RoleRepository;
import com.sketchtrench.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a full round through the real engine (scheduler auto-pick included) against a real
 * PostgreSQL, verifying the round/guess/scoring/history/progression pipeline persists end-to-end.
 * Skips on machines without Docker (see {@code disabledWithoutDocker}).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GameFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RoomService roomService;
    @Autowired GameService gameService;
    @Autowired JdbcTemplate jdbc;

    private User newUser(String name) {
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@test.local");
        u.setPassword(passwordEncoder.encode("password123"));
        u.setDisplayName(name);
        u.getRoles().add(roleRepository.findByName("PLAYER").orElseThrow());
        return userRepository.save(u);
    }

    private Long wordForRound(Long roomId) {
        List<Long> rows = jdbc.queryForList(
                "SELECT word_id FROM rounds WHERE room_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, roomId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String wordText(Long wordId) {
        return jdbc.queryForObject("SELECT text FROM words WHERE id = ?", String.class, wordId);
    }

    @Test
    void playsFullRoundAndPersistsProgression() throws Exception {
        User alice = newUser("alice");
        User bob = newUser("bob");

        RoomDto.RoomResponse room = roomService.create(alice.getId(),
                new RoomDto.CreateRoomRequest("IT Room", null, null, null, 4,
                        new RoomDto.SettingsRequest(20, 1, false, false, false, 3)));
        Long roomId = room.id();

        roomService.join(roomId, bob.getId(), new RoomDto.JoinRoomRequest(null, null));
        roomService.setReady(roomId, bob.getId(), true);

        gameService.startGame(roomId, alice.getId());

        // auto-pick window is 10s; round must be live with a word after that
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Long wordId = null;
        while (System.nanoTime() < deadline) {
            wordId = wordForRound(roomId);
            if (wordId != null) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(wordId).as("auto-pick should select a word").isNotNull();
        String word = wordText(wordId);

        // wrong guess -> no points, no correct-answer record
        GameDto.CorrectGuess wrong = gameService.submitGuess(roomId, bob.getId(), "zzzz");
        assertThat(wrong).isNull();

        // drawer cannot guess their own word
        org.junit.jupiter.api.Assertions.assertThrows(com.sketchtrench.exception.ConflictException.class,
                () -> gameService.submitGuess(roomId, alice.getId(), word));

        // correct guess with a typo tolerated
        GameDto.CorrectGuess hit = gameService.submitGuess(roomId, bob.getId(), word + "s");
        assertThat(hit).isNotNull();
        assertThat(hit.points()).isGreaterThan(0);

        // bob is the only guesser -> round ends immediately, game over (1 round)
        long gDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < gDeadline) {
            Integer g = jdbc.queryForObject(
                    "SELECT count(*) FROM game_history WHERE room_id = ?", Integer.class, roomId);
            if (g != null && g > 0) {
                break;
            }
            Thread.sleep(100);
        }

        // game history + players recorded
        Long gameId = jdbc.queryForObject(
                "SELECT id FROM game_history WHERE room_id = ? ORDER BY id DESC LIMIT 1", Long.class, roomId);
        assertThat(gameId).isNotNull();
        Integer players = jdbc.queryForObject(
                "SELECT count(*) FROM game_history_players WHERE game_id = ?", Integer.class, gameId);
        assertThat(players).isEqualTo(2);
        Long winner = jdbc.queryForObject(
                "SELECT winner_id FROM game_history WHERE id = ?", Long.class, gameId);
        assertThat(winner).isEqualTo(bob.getId());

        // classic game: elo untouched, deltas recorded as 0, XP gained
        Integer bobElo = jdbc.queryForObject("SELECT elo_rating FROM users WHERE id = ?", Integer.class, bob.getId());
        assertThat(bobElo).isEqualTo(1200);
        Integer bobXp = jdbc.queryForObject("SELECT xp FROM users WHERE id = ?", Integer.class, bob.getId());
        assertThat(bobXp).isGreaterThanOrEqualTo(50); // participation + guess + win
    }
}
