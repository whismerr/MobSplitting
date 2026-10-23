package io.github.whismerr.mobsplitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Health and movement-speed rules for offspring. */
class StatRulesTest {

    private static final double EPSILON = 1e-9;

    @Test
    void stageTwoHasHalfTheOriginalMaxHealth() {
        assertEquals(10.0, SplitRules.childMaxHealth(20.0, 2), EPSILON); // zombie
        assertEquals(5.0, SplitRules.childMaxHealth(10.0, 2), EPSILON);  // pig
        assertEquals(50.0, SplitRules.childMaxHealth(100.0, 2), EPSILON); // iron golem
    }

    @Test
    void stageThreeHasAQuarterOfTheOriginalMaxHealth() {
        assertEquals(5.0, SplitRules.childMaxHealth(20.0, 3), EPSILON);
        assertEquals(2.5, SplitRules.childMaxHealth(10.0, 3), EPSILON);
        assertEquals(25.0, SplitRules.childMaxHealth(100.0, 3), EPSILON);
    }

    @Test
    void eachGenerationHasHalfItsParentsMaxHealth() {
        for (double origin : new double[] {8.0, 20.0, 30.0, 100.0}) {
            double stage2 = SplitRules.childMaxHealth(origin, 2);
            double stage3 = SplitRules.childMaxHealth(origin, 3);
            assertEquals(origin / 2, stage2, EPSILON);
            assertEquals(stage2 / 2, stage3, EPSILON);
        }
    }

    @Test
    void stageTwoIs25PercentFasterThanTheOriginal() {
        assertEquals(0.23 * 1.25, SplitRules.childMovementSpeed(0.23, 2), EPSILON); // zombie
        assertEquals(0.25 * 1.25, SplitRules.childMovementSpeed(0.25, 2), EPSILON);
    }

    @Test
    void stageThreeIs50PercentFasterThanTheOriginalNotCompounded() {
        assertEquals(0.23 * 1.5, SplitRules.childMovementSpeed(0.23, 3), EPSILON);
        // 1.25 * 1.25 = 1.5625 would be the wrong, compounded result.
        double compounded = SplitRules.childMovementSpeed(SplitRules.childMovementSpeed(0.2, 2), 2);
        assertTrue(Math.abs(SplitRules.childMovementSpeed(0.2, 3) - compounded) > 1e-3);
    }

    @Test
    void stageFactors() {
        assertEquals(1.0, SplitRules.stageHealthFactor(1), EPSILON);
        assertEquals(0.5, SplitRules.stageHealthFactor(2), EPSILON);
        assertEquals(0.25, SplitRules.stageHealthFactor(3), EPSILON);
        assertEquals(1.0, SplitRules.stageSpeedFactor(1), EPSILON);
        assertEquals(1.25, SplitRules.stageSpeedFactor(2), EPSILON);
        assertEquals(1.5, SplitRules.stageSpeedFactor(3), EPSILON);
    }

    @Test
    void invalidStagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SplitRules.stageHealthFactor(0));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.stageSpeedFactor(4));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.childMaxHealth(20.0, 4));
    }

    @Test
    void invalidOriginValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SplitRules.childMaxHealth(0.0, 2));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.childMaxHealth(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.childMovementSpeed(-0.1, 2));
        assertThrows(IllegalArgumentException.class, () -> SplitRules.childMovementSpeed(Double.POSITIVE_INFINITY, 2));
    }

    @Test
    void valuesAreClampedToMinecraftLimits() {
        assertEquals(SplitRules.MIN_MAX_HEALTH, SplitRules.childMaxHealth(2.0, 3), EPSILON);
        assertEquals(SplitRules.MAX_MAX_HEALTH, SplitRules.childMaxHealth(5000.0, 2), EPSILON);
        assertEquals(0.0, SplitRules.childMovementSpeed(0.0, 3), EPSILON); // stationary mobs stay still
        assertEquals(SplitRules.MAX_MOVEMENT_SPEED, SplitRules.childMovementSpeed(1000.0, 3), EPSILON);
    }

    @Test
    void storedOriginValuesWinOverTheParentsOwn() {
        // A stage-2 parent with 10 max health and 1.25x speed, whose original had 20 and 0.23.
        assertEquals(20.0, SplitRules.resolveOriginHealth(20.0, 10.0), EPSILON);
        assertEquals(0.23, SplitRules.resolveOriginSpeed(0.23, 0.23 * 1.25), EPSILON);
    }

    @Test
    void missingOrCorruptStoredValuesFallBackToTheParent() {
        assertEquals(20.0, SplitRules.resolveOriginHealth(null, 20.0), EPSILON);
        assertEquals(20.0, SplitRules.resolveOriginHealth(Double.NaN, 20.0), EPSILON);
        assertEquals(20.0, SplitRules.resolveOriginHealth(-3.0, 20.0), EPSILON);
        assertEquals(0.23, SplitRules.resolveOriginSpeed(null, 0.23), EPSILON);
        assertEquals(0.23, SplitRules.resolveOriginSpeed(-1.0, 0.23), EPSILON);
    }

    @Test
    void noUsableValueResolvesToNaN() {
        assertTrue(Double.isNaN(SplitRules.resolveOriginHealth(null, Double.NaN)));
        assertTrue(Double.isNaN(SplitRules.resolveOriginHealth(0.0, 0.0)));
        assertTrue(Double.isNaN(SplitRules.resolveOriginSpeed(null, Double.NaN)));
    }

    /**
     * Simulates a full split chain the way OffspringSpawner does it: the origin values are read
     * from the original, stored on each child, and re-read from the stage-2 parent.
     */
    @Test
    void fullChainUsesOriginalValuesAndNeverCompounds() {
        double originHealth = 20.0;
        double originSpeed = 0.23;

        // Stage 1 -> 2: the original has no stored values, so its own are used.
        double h1 = SplitRules.resolveOriginHealth(null, originHealth);
        double s1 = SplitRules.resolveOriginSpeed(null, originSpeed);
        double stage2Health = SplitRules.childMaxHealth(h1, 2);
        double stage2Speed = SplitRules.childMovementSpeed(s1, 2);
        assertEquals(10.0, stage2Health, EPSILON);
        assertEquals(0.2875, stage2Speed, EPSILON);

        // Stage 2 -> 3: stored origin values are used, not the parent's already-changed ones.
        double h2 = SplitRules.resolveOriginHealth(h1, stage2Health);
        double s2 = SplitRules.resolveOriginSpeed(s1, stage2Speed);
        assertEquals(5.0, SplitRules.childMaxHealth(h2, 3), EPSILON);
        assertEquals(0.345, SplitRules.childMovementSpeed(s2, 3), EPSILON);

        // Recomputing (e.g. after a reload) gives the same absolute values, never a further multiple.
        assertEquals(5.0, SplitRules.childMaxHealth(SplitRules.resolveOriginHealth(h2, 5.0), 3), EPSILON);
        assertEquals(0.345, SplitRules.childMovementSpeed(SplitRules.resolveOriginSpeed(s2, 0.345), 3), EPSILON);
    }
}
