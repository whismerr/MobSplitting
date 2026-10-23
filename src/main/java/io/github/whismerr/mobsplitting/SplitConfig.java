package io.github.whismerr.mobsplitting;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/** Validated, immutable snapshot of config.yml. */
public record SplitConfig(
        boolean enabled,
        boolean useScaleAttribute,
        double stage2Scale,
        double stage3Scale,
        boolean babyFallback,
        boolean copyNames,
        boolean copyEquipment,
        boolean inheritTarget,
        Set<EntityType> excludedTypes,
        Set<String> excludedWorlds,
        Set<SpawnReason> excludedSpawnReasons,
        boolean splitTamedPets,
        boolean requirePlayerKill,
        int maxNearbyMobs,
        double nearbyRadius,
        int maxSplitsPerTick,
        boolean debug) {

    /** Reads and validates the configuration, logging a warning for every invalid value. */
    public static SplitConfig load(FileConfiguration config, Logger logger) {
        double stage2 = readScale(config, "scale.stage-2", 0.65, logger);
        double stage3 = readScale(config, "scale.stage-3", 0.4, logger);
        if (stage3 > stage2) {
            logger.warning("scale.stage-3 (" + stage3 + ") is larger than scale.stage-2 (" + stage2
                    + "). Stage-3 mobs will be bigger than their parents.");
        }

        Set<EntityType> excludedTypes = EnumSet.noneOf(EntityType.class);
        for (String name : config.getStringList("excluded-mobs")) {
            String normalized = name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
            try {
                excludedTypes.add(EntityType.valueOf(normalized));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown mob type in excluded-mobs: '" + name + "' (ignored)");
            }
        }

        Set<String> excludedWorlds = new HashSet<>();
        for (String world : config.getStringList("excluded-worlds")) {
            excludedWorlds.add(world.trim().toLowerCase(Locale.ROOT));
        }

        Set<SpawnReason> excludedReasons = EnumSet.noneOf(SpawnReason.class);
        for (String name : config.getStringList("excluded-spawn-reasons")) {
            try {
                excludedReasons.add(SpawnReason.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown spawn reason in excluded-spawn-reasons: '" + name + "' (ignored)");
            }
        }

        int maxNearby = config.getInt("performance.max-nearby-mobs", 80);
        double radius = config.getDouble("performance.nearby-radius", 16.0);
        int maxPerTick = config.getInt("performance.max-splits-per-tick", 20);
        if (radius <= 0 || radius > 128) {
            logger.warning("performance.nearby-radius must be between 0 and 128; using 16.");
            radius = 16.0;
        }
        if (maxPerTick < 1) {
            logger.warning("performance.max-splits-per-tick must be at least 1; using 20.");
            maxPerTick = 20;
        }

        return new SplitConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("scale.use-scale-attribute", true),
                stage2,
                stage3,
                config.getBoolean("scale.baby-fallback", true),
                config.getBoolean("offspring.copy-names", true),
                config.getBoolean("offspring.copy-equipment", true),
                config.getBoolean("offspring.inherit-target", true),
                Collections.unmodifiableSet(excludedTypes),
                Collections.unmodifiableSet(excludedWorlds),
                Collections.unmodifiableSet(excludedReasons),
                config.getBoolean("split-tamed-pets", false),
                config.getBoolean("require-player-kill", false),
                maxNearby,
                radius,
                maxPerTick,
                config.getBoolean("debug", false));
    }

    private static double readScale(FileConfiguration config, String path, double fallback, Logger logger) {
        double value = config.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < SplitRules.MIN_SCALE || value > SplitRules.MAX_SCALE) {
            logger.warning(path + " must be between " + SplitRules.MIN_SCALE + " and " + SplitRules.MAX_SCALE
                    + "; using " + fallback + ".");
            return fallback;
        }
        return value;
    }
}
