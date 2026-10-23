package io.github.whismerr.mobsplitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SplitRulesTest {

    private static final double EPSILON = 1e-9;

    @Test
    void mobWithoutMarkerIsOriginal() {
        assertEquals(SplitRules.ORIGINAL_STAGE, SplitRules.resolveStage(null));
    }

    @Test
    void validMarkersAreKept() {
        assertEquals(2, SplitRules.resolveStage(2));
        assertEquals(3, SplitRules.resolveStage(3));
    }

    @Test
    void corruptMarkersAreTreatedAsFinalStage() {
        assertEquals(SplitRules.FINAL_STAGE, SplitRules.resolveStage(0));
        assertEquals(SplitRules.FINAL_STAGE, SplitRules.resolveStage(-5));
        assertEquals(SplitRules.FINAL_STAGE, SplitRules.resolveStage(4));
        assertEquals(SplitRules.FINAL_STAGE, SplitRules.resolveStage(Integer.MAX_VALUE));
    }

    @Test
    void stageOneSplitsIntoStageTwo() {
        assertEquals(OptionalInt.of(2), SplitRules.childStage(1));
    }

    @Test
    void stageTwoSplitsIntoStageThree() {
        assertEquals(OptionalInt.of(3), SplitRules.childStage(2));
    }

    @Test
    void stageThreeNeverSplits() {
        assertFalse(SplitRules.canSplit(3));
        assertTrue(SplitRules.childStage(3).isEmpty());
    }

    @Test
    void invalidStagesNeverSplit() {
        assertFalse(SplitRules.canSplit(0));
        assertFalse(SplitRules.canSplit(4));
        assertTrue(SplitRules.childStage(99).isEmpty());
    }

    /** Simulates killing one original mob and every offspring it produces. */
    @Test
    void killingOneOriginalProducesTwoThenFourMobsAndStops() {
        int[] deathsPerStage = new int[SplitRules.FINAL_STAGE + 1];
        Deque<Integer> alive = new ArrayDeque<>();
        alive.add(SplitRules.resolveStage(null));
        int iterations = 0;
        while (!alive.isEmpty()) {
            assertTrue(++iterations < 100, "splitting did not terminate");
            int stage = alive.poll();
            deathsPerStage[stage]++;
            OptionalInt child = SplitRules.childStage(stage);
            if (child.isPresent()) {
                for (int i = 0; i < SplitRules.OFFSPRING_PER_SPLIT; i++) {
                    alive.add(child.getAsInt());
                }
            }
        }
        assertEquals(1, deathsPerStage[1]);
        assertEquals(2, deathsPerStage[2]);
        assertEquals(4, deathsPerStage[3]);
        assertEquals(7, iterations);
    }

    @Test
    void mobsAtStageMatchesSpecification() {
        assertEquals(1, SplitRules.mobsAtStage(1));
        assertEquals(2, SplitRules.mobsAtStage(2));
        assertEquals(4, SplitRules.mobsAtStage(3));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.mobsAtStage(4));
    }

    @Test
    void scalesAreRelativeToTheOriginal() {
        assertEquals(0.65, SplitRules.childScale(1.0, 2, 0.65, 0.4), EPSILON);
        // Stage 3 is 0.4 of the ORIGINAL, not 0.4 of stage 2.
        assertEquals(0.4, SplitRules.childScale(1.0, 3, 0.65, 0.4), EPSILON);
        assertEquals(1.3, SplitRules.childScale(2.0, 2, 0.65, 0.4), EPSILON);
    }

    @Test
    void invalidOriginScaleFallsBackToOne() {
        assertEquals(0.65, SplitRules.childScale(Double.NaN, 2, 0.65, 0.4), EPSILON);
        assertEquals(0.65, SplitRules.childScale(0.0, 2, 0.65, 0.4), EPSILON);
        assertEquals(0.65, SplitRules.childScale(-1.0, 2, 0.65, 0.4), EPSILON);
    }

    @Test
    void scaleIsClampedToMinecraftLimits() {
        assertEquals(SplitRules.MIN_SCALE, SplitRules.childScale(0.1, 3, 0.65, 0.4), EPSILON);
        assertEquals(SplitRules.MAX_SCALE, SplitRules.childScale(15.0, 2, 2.0, 0.4), EPSILON);
        assertEquals(1.0, SplitRules.clampScale(Double.POSITIVE_INFINITY), EPSILON);
    }

    @Test
    void stageScaleFactorRejectsInvalidStage() {
        assertThrows(IllegalArgumentException.class, () -> SplitRules.stageScaleFactor(0, 0.65, 0.4));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.stageScaleFactor(4, 0.65, 0.4));
    }
}
