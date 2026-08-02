package com.sketchtrench.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Per-room game tunables. {@code @MapsId} shares the primary key with the parent room
 * (room_id is PK + FK) — a textbook 1:1 mapped to one row, nothing to drift.
 */
@Entity
@Table(name = "room_settings")
@Getter
@Setter
@NoArgsConstructor
public class RoomSettings {

    @Id
    private Long roomId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "drawing_time_sec", nullable = false)
    private int drawingTimeSec = 80;

    @Column(nullable = false)
    private int rounds = 3;

    @Column(name = "hints_enabled", nullable = false)
    private boolean hintsEnabled = true;

    @Column(name = "allow_spectators", nullable = false)
    private boolean allowSpectators = true;

    @Column(name = "custom_words", nullable = false)
    private boolean customWords = false;

    @Column(name = "word_count", nullable = false)
    private int wordCount = 3;

    @Column(name = "updated_at", nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant updatedAt;

    public static RoomSettings forRoom(Room room) {
        RoomSettings settings = new RoomSettings();
        settings.setRoom(room);
        return settings;
    }
}
