package com.sketchtrench.social.entity;

import com.sketchtrench.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A confirmed friendship edge. Stored ONCE with {@code user_a_id < user_b_id}: no
 * mirrored rows, no duplicate pairs; "are X and Y friends?" is a single lookup.
 * Composite PK via {@code @IdClass}.
 */
@Entity
@Table(name = "friends")
@IdClass(Friend.FriendId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Friend {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id")
    private User userA;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id")
    private User userB;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant createdAt;

    @AllArgsConstructor
    @NoArgsConstructor
    public static class FriendId implements Serializable {
        private Long userA;
        private Long userB;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FriendId that)) return false;
            return Objects.equals(userA, that.userA) && Objects.equals(userB, that.userB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userA, userB);
        }
    }
}
