package com.sketchtrench.game.entity;

import com.sketchtrench.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * The FINAL canvas snapshot of a round, stored as JSON vector strokes. Live strokes
 * travel over WebSocket (fast, ephemeral); this row powers replay and "drawing of the
 * day" without re-watching the stream.
 */
@Entity
@Table(name = "drawings")
@Getter
@Setter
@NoArgsConstructor
public class Drawing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id")
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drawer_id")
    private User drawer;

    @Column(name = "snapshot_type", nullable = false, length = 8)
    private String snapshotType = "JSON";

    /** jsonb column; the app stores an array of stroke events. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String data;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;
}
