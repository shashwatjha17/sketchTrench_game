package com.sketchtrench.game.entity;

import com.sketchtrench.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Per-player line of a finished game: placement, points, ELO delta. */
@Entity
@Table(name = "game_history_players")
@IdClass(GameHistoryPlayer.GameHistoryPlayerId.class)
@Getter
@Setter
@NoArgsConstructor
public class GameHistoryPlayer {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private GameHistory game;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int points;

    @Column(name = "elo_delta", nullable = false)
    private int eloDelta;

    public static class GameHistoryPlayerId implements Serializable {
        private Long game;
        private Long user;

        public GameHistoryPlayerId() {
        }

        public GameHistoryPlayerId(Long game, Long user) {
            this.game = game;
            this.user = user;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GameHistoryPlayerId that)) return false;
            return Objects.equals(game, that.game) && Objects.equals(user, that.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(game, user);
        }
    }
}
