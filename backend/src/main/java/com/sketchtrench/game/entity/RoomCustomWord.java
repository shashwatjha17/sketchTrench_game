package com.sketchtrench.game.entity;

import com.sketchtrench.room.entity.Room;
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
 * A word the room host added for a {@code customWords} game. Lives outside the global
 * {@code words} pool so different rooms can reuse the same text.
 */
@Entity
@Table(name = "room_custom_words")
@Getter
@Setter
@NoArgsConstructor
public class RoomCustomWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false, length = 64)
    private String word;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;
}
