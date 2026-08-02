package com.sketchtrench.user.repository;

import com.sketchtrench.user.entity.League;
import com.sketchtrench.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    long countByCreatedAtAfter(Instant since);

    /** Username prefix search for "add friend / find player". Case-insensitive. */
    List<User> findByUsernameContainingIgnoreCase(String query);

    /**
     * Fetch the user WITH their roles in one query (JOIN FETCH), avoiding the
     * LazyInitializationException trap during authentication.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    List<User> findByOrderByEloRatingDesc(Pageable pageable);

    List<User> findByLeagueOrderByEloRatingDesc(League league, Pageable pageable);

}