package com.sketchtrench.game.entity;

import com.sketchtrench.room.entity.Room;
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

/** One drawing+guessing phase in a room's game. */
@Entity
@Table(name = "rounds",
        uniqueConstraints = @UniqueConstraint(name = "uq_rounds_room_round",
                columnNames = {"room_id", "round_num"}))
@Getter
@Setter
@NoArgsConstructor
public class Round {

    public enum Status { DRAWING, GUESSING, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id")
    private Word word;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drawer_id")
    private User drawer;

    @Column(name = "round_num", nullable = false)
    private int roundNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAWING;

    @Column(name = "started_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant startedAt;

    @Column(name = "ended_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant endedAt;
}
