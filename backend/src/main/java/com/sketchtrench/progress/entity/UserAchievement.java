package com.sketchtrench.progress.entity;

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

/** A player's progress toward (and ownership of) an achievement. */
@Entity
@Table(name = "user_achievements")
@IdClass(UserAchievement.UserAchievementId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievement {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id")
    private Achievement achievement;

    @Column(nullable = false)
    private int progress;

    @Column(name = "unlocked_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private Instant unlockedAt;

    @NoArgsConstructor
    public static class UserAchievementId implements Serializable {
        private Long user;
        private Long achievement;

        public UserAchievementId(Long user, Long achievement) {
            this.user = user;
            this.achievement = achievement;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserAchievementId that)) return false;
            return Objects.equals(user, that.user) && Objects.equals(achievement, that.achievement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, achievement);
        }
    }
}
