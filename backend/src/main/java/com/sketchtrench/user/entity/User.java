package com.sketchtrench.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A registered account. Mirrors the {@code users} table exactly — Hibernate runs
 * with {@code ddl-auto=validate}, so any drift between this mapping and the Flyway
 * schema fails the application startup instead of corrupting data silently.
 *
 * <p>Why constructor injection via Lombok on the class, field-level defaults here?
 * Hibernate inserts EVERY primitive field, so inline defaults ({@code level = 1},
 * {@code eloRating = 1200}) guarantee a fresh user gets sane values even if a code
 * path forgets to set them. The DB CHECK constraints are the second safety net.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash — never a raw password. */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "avatar_color", nullable = false, length = 7)
    private String avatarColor = "#6d5dfc";

    @Column(name = "avatar_expression", nullable = false, length = 16)
    private String avatarExpression = "happy";

    @Column(name = "avatar_sunglasses", nullable = false)
    private boolean avatarSunglasses;

    @Column(name = "avatar_wig", nullable = false, length = 16)
    private String avatarWig = "none";

    @Column(length = 500)
    private String bio;

    @Column(nullable = false)
    private int xp;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "elo_rating", nullable = false)
    private int eloRating = 1200;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private League league = League.BRONZE;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_status", nullable = false, length = 16)
    private OnlineStatus onlineStatus = OnlineStatus.OFFLINE;

    @Column(name = "last_seen_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant lastSeenAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    @Column(name = "banned_until")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant bannedUntil;

    @Column(name = "muted_until")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant mutedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant updatedAt;

    /**
     * Many-to-many to {@code user_roles}. LAZY: roles are only fetched when actually
     * needed (login), never as part of every user query.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(String role) {
        return roles.stream().anyMatch(r -> r.getName().equalsIgnoreCase(role));
    }

    /** Hibernate lifecycle callback: runs on INSERT, before the row is written. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Hibernate lifecycle callback: runs on UPDATE, before the row is written. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
