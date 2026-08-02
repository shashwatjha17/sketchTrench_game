package com.sketchtrench.progress.service;

import com.sketchtrench.progress.ProgressTracker.PlayerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionServiceTest {

    private static PlayerResult result(long id, int position, int points, int eloBefore) {
        return new PlayerResult(id, position, points, eloBefore);
    }

    @Test
    void recomputeEloIsZeroSumForEqualOpponents() {
        Map<Long, Integer> ratings = ProgressionService.recomputeElo(List.of(
                result(1, 1, 150, 1200),
                result(2, 2, 100, 1200)));
        // winner gets +16, loser -16 (K=32, expected 0.5 each)
        assertThat(ratings.get(1L) - 1200).isEqualTo(16);
        assertThat(ratings.get(2L) - 1200).isEqualTo(-16);
        // sum of deltas is zero
        assertThat((ratings.get(1L) - 1200) + (ratings.get(2L) - 1200)).isZero();
    }

    @Test
    void recomputeEloRewardsUpset() {
        // 1400-rated player loses to 1000-rated -> winner gains MORE than 16
        Map<Long, Integer> ratings = ProgressionService.recomputeElo(List.of(
                result(1, 1, 120, 1000),
                result(2, 2, 80, 1400)));
        assertThat(ratings.get(1L) - 1000).isGreaterThan(16);
        assertThat(ratings.get(2L) - 1400).isNegative();
    }

    @Test
    void recomputeEloNeedsAtLeastTwoPlayers() {
        assertThat(ProgressionService.recomputeElo(List.of(result(1, 1, 100, 1200)))).isEmpty();
    }

    @Test
    void recomputeEloOrderIsStableForThreePlayers() {
        Map<Long, Integer> ratings = ProgressionService.recomputeElo(List.of(
                result(1, 1, 200, 1200),
                result(2, 2, 120, 1200),
                result(3, 3, 60, 1200)));
        assertThat(ratings.get(1L)).isGreaterThan(1200);
        assertThat(ratings.get(3L)).isLessThan(1200);
    }

    @Test
    void levelForXpFollowsSqrtCurve() {
        assertThat(ProgressionService.levelForXp(0)).isEqualTo(1);
        assertThat(ProgressionService.levelForXp(99)).isEqualTo(1);
        assertThat(ProgressionService.levelForXp(100)).isEqualTo(2);
        assertThat(ProgressionService.levelForXp(245)).isEqualTo(2);
        assertThat(ProgressionService.levelForXp(400)).isEqualTo(3);
        assertThat(ProgressionService.levelForXp(900)).isEqualTo(4);
    }
}
