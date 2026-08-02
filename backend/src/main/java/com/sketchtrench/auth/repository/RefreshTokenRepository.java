package com.sketchtrench.auth.repository;

import com.sketchtrench.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndRevokedTrue(Long userId);

    void deleteByExpiresAtBefore(Instant now);

    List<RefreshToken> findByUserId(Long userId);
}
