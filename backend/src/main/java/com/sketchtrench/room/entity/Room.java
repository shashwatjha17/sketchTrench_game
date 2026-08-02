package com.sketchtrench.room.entity;

import com.sketchtrench.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A game room = the container for one play session: lobby -> playing -> finished.
 * Per-room tunables live in {@link RoomSettings} (1:1); membership in {@link RoomMember}.
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    public enum Mode { CLASSIC, RANKED, TEAMS, RAPID, CUSTOM }

    public enum Visibility { PUBLIC, PRIVATE }

    public enum Status { WAITING, PLAYING, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode = Mode.CLASSIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.WAITING;

    @Column(name = "invite_code", length = 12)
    private String inviteCode;

    @Column(name = "is_password_protected", nullable = false)
    private boolean passwordProtected;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers = 8;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant updatedAt;

    @OneToOne(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private RoomSettings settings;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomMember> members = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
