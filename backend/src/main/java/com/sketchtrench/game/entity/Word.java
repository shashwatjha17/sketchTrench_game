package com.sketchtrench.game.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One drawing prompt. {@code timesUsed} grows each time the word is picked, letting the
 * word picker favour fresher words. {@code active=false} is the admin soft-delete.
 */
@Entity
@Table(name = "words")
@Getter
@Setter
@NoArgsConstructor
public class Word {

    public enum Difficulty { EASY, MEDIUM, HARD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private WordCategory category;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "times_used", nullable = false)
    private int timesUsed;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;
}
