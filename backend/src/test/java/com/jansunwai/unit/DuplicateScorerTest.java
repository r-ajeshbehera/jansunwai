package com.jansunwai.unit;

import com.jansunwai.config.AppProperties;
import com.jansunwai.service.DuplicateScorer;
import com.jansunwai.support.TestProps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DuplicateScorerTest {
    private final AppProperties.Duplicates cfg = TestProps.duplicates();   // minSemantic 0.40, minTotal 0.55, 500 m

    @Test
    void weightedTotal() {
        var s = DuplicateScorer.score(0.80, 100, "ROAD_DAMAGE", "ROAD_DAMAGE", cfg).orElseThrow();
        // 0.60*0.80 + 0.25*(1 - 100/500) + 0.15*1 = 0.48 + 0.20 + 0.15
        assertThat(s.total()).isCloseTo(0.83, within(0.001));
        assertThat(s.geo()).isCloseTo(0.80, within(0.001));
    }

    @Test
    void lowSemanticSimilarityIsNeverACandidate() {
        assertThat(DuplicateScorer.score(0.39, 0, "ROAD_DAMAGE", "ROAD_DAMAGE", cfg)).isEmpty();
    }

    @Test
    void knownDistanceOutsideRadiusIsADifferentPlace() {
        assertThat(DuplicateScorer.score(0.99, 501, "ROAD_DAMAGE", "ROAD_DAMAGE", cfg)).isEmpty();
    }

    @Test
    void unknownLocationIsNeutral() {
        var s = DuplicateScorer.score(0.80, null, "GARBAGE", "GARBAGE", cfg).orElseThrow();
        assertThat(s.geo()).isEqualTo(0.5);
    }

    @Test
    void differentCategoryLowersTheScore() {
        var same = DuplicateScorer.score(0.70, 50, "DRAINAGE", "DRAINAGE", cfg).orElseThrow();
        var diff = DuplicateScorer.score(0.70, 50, "DRAINAGE", "GARBAGE", cfg);
        assertThat(diff).isPresent();
        assertThat(diff.get().total()).isLessThan(same.total());
        assertThat(diff.get().category()).isZero();
    }

    @Test
    void belowMinimumTotalIsDropped() {
        // 0.6*0.45 + 0.25*0.02 + 0 = 0.275 < 0.55
        assertThat(DuplicateScorer.score(0.45, 490, "DRAINAGE", "GARBAGE", cfg)).isEmpty();
    }

    @Test
    void weightsSumToOne() {
        assertThat(DuplicateScorer.W_SEMANTIC + DuplicateScorer.W_GEO + DuplicateScorer.W_CATEGORY).isCloseTo(1.0, within(1e-9));
    }
}
