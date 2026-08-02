package com.sketchtrench.room.entity;

import com.sketchtrench.user.entity.User;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Join table between users and rooms. Carries per-room state: role, ready flag, running
 * score, and live-connection status (drives the presence UI and reconnect logic).
 */
@Entity
@Table(name = "room_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_room_members_room_user", columnNames = {"room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RoomMember {

    public enum Role { HOST, PLAYER, SPECTATOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.PLAYER;

    @Column(name = "is_ready", nullable = false)
    private boolean ready;

    @Column(nullable = false)
    private int score;

    @Column(name = "is_connected", nullable = false)
    private boolean connected = true;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant joinedAt;
}
