package io.github.whismerr.mobsplitting;

import java.util.OptionalInt;

/**
 * Pure generation, scale, health and speed rules for mob splitting.
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

    /** Each generation has this fraction of its parent's maximum health. */
    public static final double HEALTH_FACTOR_PER_GENERATION = 0.5;
    /** Each generation moves this much faster, as a fraction of the ORIGINAL mob's base speed. */
    public static final double SPEED_BONUS_PER_GENERATION = 0.25;
    /** Smallest value accepted by the vanilla minecraft:max_health attribute. */
    public static final double MIN_MAX_HEALTH = 1.0;
    /** Largest value accepted by the vanilla minecraft:max_health attribute. */
    public static final double MAX_MAX_HEALTH = 1024.0;
    /** Largest value accepted by the vanilla minecraft:movement_speed attribute. */
    public static final double MAX_MOVEMENT_SPEED = 1024.0;

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

    /**
     * Returns the maximum-health factor of a stage relative to the original mob: 1, 0.5, 0.25.
     *
     * @throws IllegalArgumentException if the stage is not 1, 2 or 3
     */
    public static double stageHealthFactor(int stage) {
        return Math.pow(HEALTH_FACTOR_PER_GENERATION, generationsAfterOriginal(stage));
    }

    /**
     * Returns the movement-speed factor of a stage relative to the original mob: 1, 1.25, 1.5.
     *
     * @throws IllegalArgumentException if the stage is not 1, 2 or 3
     */
    public static double stageSpeedFactor(int stage) {
        return 1.0 + SPEED_BONUS_PER_GENERATION * generationsAfterOriginal(stage);
    }

    /**
     * Computes the absolute maximum health for an offspring.
     *
     * @param originMaxHealth the maximum health of the original stage-1 mob; must be finite and positive
     * @param childStage the offspring's stage (2 or 3)
     * @return the maximum health, clamped to the range accepted by Minecraft
     */
    public static double childMaxHealth(double originMaxHealth, int childStage) {
        if (!isValidHealth(originMaxHealth)) {
            throw new IllegalArgumentException("Invalid origin max health: " + originMaxHealth);
        }
        double health = originMaxHealth * stageHealthFactor(childStage);
        return Math.max(MIN_MAX_HEALTH, Math.min(MAX_MAX_HEALTH, health));
    }

    /**
     * Computes the absolute base movement speed for an offspring.
     *
     * @param originSpeed the base movement speed of the original stage-1 mob; must be finite and not negative
     * @param childStage the offspring's stage (2 or 3)
     * @return the speed, clamped to the range accepted by Minecraft
     */
    public static double childMovementSpeed(double originSpeed, int childStage) {
        if (!isValidSpeed(originSpeed)) {
            throw new IllegalArgumentException("Invalid origin movement speed: " + originSpeed);
        }
        return Math.min(MAX_MOVEMENT_SPEED, originSpeed * stageSpeedFactor(childStage));
    }

    /**
     * Picks the original mob's maximum health from a stored value, falling back to {@code fallback}
     * (the parent's own value, correct for originals and for offspring made before health was scaled).
     *
     * @return the origin value, or {@link Double#NaN} if neither value is usable
     */
    public static double resolveOriginHealth(Double stored, double fallback) {
        if (stored != null && isValidHealth(stored)) {
            return stored;
        }
        return isValidHealth(fallback) ? fallback : Double.NaN;
    }

    /**
     * Picks the original mob's base movement speed; see {@link #resolveOriginHealth}.
     *
     * @return the origin value, or {@link Double#NaN} if neither value is usable
     */
    public static double resolveOriginSpeed(Double stored, double fallback) {
        if (stored != null && isValidSpeed(stored)) {
            return stored;
        }
        return isValidSpeed(fallback) ? fallback : Double.NaN;
    }

    private static boolean isValidHealth(double health) {
        return Double.isFinite(health) && health > 0;
    }

    private static boolean isValidSpeed(double speed) {
        return Double.isFinite(speed) && speed >= 0;
    }

    private static int generationsAfterOriginal(int stage) {
        if (stage < ORIGINAL_STAGE || stage > FINAL_STAGE) {
            throw new IllegalArgumentException("Invalid stage: " + stage);
        }
        return stage - ORIGINAL_STAGE;
    }
}
