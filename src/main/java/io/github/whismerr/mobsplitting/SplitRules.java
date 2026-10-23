package io.github.whismerr.mobsplitting;

import java.util.OptionalInt;

/**
 * Pure generation and scale rules for mob splitting.
 *
 * <p>This class has no Bukkit dependencies so it can be unit tested without a server.
 *
 * <ul>
 *   <li>Stage 1: an original mob. Splits into {@link #OFFSPRING_PER_SPLIT} stage-2 mobs.</li>
 *   <li>Stage 2: splits into {@link #OFFSPRING_PER_SPLIT} stage-3 mobs.</li>
 *   <li>Stage 3: final stage, never splits.</li>
 * </ul>
 */
public final class SplitRules {

    /** Stage of a mob that has no stage marker, i.e. any mob not created by this plugin. */
    public static final int ORIGINAL_STAGE = 1;
    /** Last stage. Mobs at this stage never split. */
    public static final int FINAL_STAGE = 3;
    /** Number of offspring created by every split. */
    public static final int OFFSPRING_PER_SPLIT = 2;

    /** Smallest value accepted by the vanilla minecraft:scale attribute. */
    public static final double MIN_SCALE = 0.0625;
    /** Largest value accepted by the vanilla minecraft:scale attribute. */
    public static final double MAX_SCALE = 16.0;

    private SplitRules() {
    }

    /**
     * Converts a stored stage marker into a stage.
     *
     * @param stored the value from the entity's persistent data, or {@code null} if absent
     * @return {@link #ORIGINAL_STAGE} when absent; {@link #FINAL_STAGE} when the value is
     *     out of range (a corrupt marker must never cause extra splitting)
     */
    public static int resolveStage(Integer stored) {
        if (stored == null) {
            return ORIGINAL_STAGE;
        }
        if (stored < ORIGINAL_STAGE || stored > FINAL_STAGE) {
            return FINAL_STAGE;
        }
        return stored;
    }

    /** Returns whether a mob at the given stage splits when it dies. */
    public static boolean canSplit(int stage) {
        return stage >= ORIGINAL_STAGE && stage < FINAL_STAGE;
    }

    /** Returns the stage of the offspring of a mob at the given stage, or empty if it does not split. */
    public static OptionalInt childStage(int stage) {
        return canSplit(stage) ? OptionalInt.of(stage + 1) : OptionalInt.empty();
    }

    /**
     * Returns the size factor of a stage relative to the original mob.
     *
     * @throws IllegalArgumentException if the stage is not 1, 2 or 3
     */
    public static double stageScaleFactor(int stage, double stage2Scale, double stage3Scale) {
        return switch (stage) {
            case 1 -> 1.0;
            case 2 -> stage2Scale;
            case 3 -> stage3Scale;
            default -> throw new IllegalArgumentException("Invalid stage: " + stage);
        };
    }

    /**
     * Computes the absolute scale attribute value for an offspring.
     *
     * @param originScale the scale of the original stage-1 mob (normally 1.0); invalid values count as 1.0
     * @param childStage the offspring's stage (2 or 3)
     * @return the scale, clamped to the range accepted by Minecraft
     */
    public static double childScale(double originScale, int childStage, double stage2Scale, double stage3Scale) {
        double origin = Double.isFinite(originScale) && originScale > 0 ? originScale : 1.0;
        return clampScale(origin * stageScaleFactor(childStage, stage2Scale, stage3Scale));
    }

    /** Clamps a scale to the range accepted by Minecraft. Non-finite values become 1.0. */
    public static double clampScale(double scale) {
        if (!Double.isFinite(scale)) {
            return 1.0;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    /** Returns how many mobs of the given stage descend from one original mob (1, 2 or 4). */
    public static int mobsAtStage(int stage) {
        if (stage < ORIGINAL_STAGE || stage > FINAL_STAGE) {
            throw new IllegalArgumentException("Invalid stage: " + stage);
        }
        int count = 1;
        for (int s = ORIGINAL_STAGE; s < stage; s++) {
            count *= OFFSPRING_PER_SPLIT;
        }
        return count;
    }
}
