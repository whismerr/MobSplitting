package io.github.whismerr.mobsplitting;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/** Spawns, positions, sizes and tags the offspring of a dying mob. */
final class OffspringSpawner {

    private final Logger logger;
    private final SplitKeys keys;
    /** Types that threw while spawning. They are skipped from then on to avoid log spam. */
    private final Set<EntityType> unspawnableTypes = EnumSet.noneOf(EntityType.class);
    /** Types already reported as "cannot be resized", so each is logged only once. */
    private final Set<EntityType> unscalableTypes = EnumSet.noneOf(EntityType.class);

    OffspringSpawner(Logger logger, SplitKeys keys) {
        this.logger = logger;
        this.keys = keys;
    }

    boolean isKnownUnspawnable(EntityType type) {
        return unspawnableTypes.contains(type);
    }

    /**
     * Spawns {@link SplitRules#OFFSPRING_PER_SPLIT} offspring around a dying parent.
     *
     * @return the number of offspring actually spawned (0 to 2)
     */
    int spawnOffspring(Mob parent, int parentStage, int childStage, SplitConfig config) {
        Location origin = parent.getLocation();
        World world = origin.getWorld();
        EntityType type = parent.getType();
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (world == null || entityClass == null || unspawnableTypes.contains(type)) {
            return 0;
        }
        // A mob that died in the void would only spawn offspring that fall and die again.
        if (origin.getY() < world.getMinHeight() || origin.getY() >= world.getMaxHeight()) {
            return 0;
        }

        double originScale = readOriginScale(parent, parentStage);
        double childScale = SplitRules.childScale(originScale, childStage, config.stage2Scale(), config.stage3Scale());
        double parentScale = currentScale(parent);
        double ratio = parentScale > 0 ? childScale / parentScale : 1.0;

        // Estimate the offspring's hitbox from the parent's, for collision checks.
        BoundingBox parentBox = parent.getBoundingBox();
        double halfWidth = Math.max(0.05, parentBox.getWidthX() * ratio / 2.0);
        double height = Math.max(0.05, parentBox.getHeight() * ratio);
        double spacing = Math.max(0.35, parentBox.getWidthX() * 0.6);

        double baseAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        int spawned = 0;
        for (int i = 0; i < SplitRules.OFFSPRING_PER_SPLIT; i++) {
            // Offspring are placed on opposite sides of where the parent died.
            double angle = baseAngle + i * (Math.PI * 2 / SplitRules.OFFSPRING_PER_SPLIT);
            Location spot = findSpot(world, origin, angle, spacing, halfWidth, height);
            Mob child = spawnOne(world, spot, entityClass, parent, childStage, originScale, childScale, config);
            if (child == null) {
                if (unspawnableTypes.contains(type)) {
                    break;
                }
                continue;
            }
            spawned++;
            if (config.inheritTarget()) {
                inheritTarget(parent, child);
            }
        }
        return spawned;
    }

    private Mob spawnOne(World world, Location spot, Class<? extends Entity> entityClass, Mob parent,
                         int childStage, double originScale, double childScale, SplitConfig config) {
        EntityType type = parent.getType();
        try {
            // randomizeData = false: no random jockeys, variants or gear. Properties come from the parent.
            // The consumer runs BEFORE the entity is added to the world, so it is never seen untagged.
            Entity entity = world.spawn(spot, entityClass, false,
                    created -> configure(created, parent, childStage, originScale, childScale, config));
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                return null;
            }
            // Another plugin may have cancelled the spawn event.
            return mob.isValid() ? mob : null;
        } catch (RuntimeException e) {
            unspawnableTypes.add(type);
            logger.log(Level.WARNING, type + " cannot be spawned as offspring; it will no longer split "
                    + "until the server restarts.", e);
            return null;
        }
    }

    private void configure(Entity created, Mob parent, int childStage, double originScale,
                           double childScale, SplitConfig config) {
        if (!(created instanceof Mob child)) {
            return;
        }
        PersistentDataContainer data = child.getPersistentDataContainer();
        data.set(keys.stage(), PersistentDataType.INTEGER, childStage);
        data.set(keys.originScale(), PersistentDataType.DOUBLE, originScale);

        PropertyCopier.copy(parent, child, config, logger);
        applySize(child, childScale, config);
        child.setRemoveWhenFarAway(parent.getRemoveWhenFarAway());
    }

    /** Shrinks the offspring with the real scale attribute, or falls back to a baby, or leaves it alone. */
    private void applySize(Mob child, double childScale, SplitConfig config) {
        AttributeInstance scale = config.useScaleAttribute() ? child.getAttribute(Attribute.SCALE) : null;
        if (scale != null) {
            scale.setBaseValue(childScale);
            return;
        }
        if (config.babyFallback() && child instanceof Ageable ageable) {
            ageable.setBaby();
            return;
        }
        if (unscalableTypes.add(child.getType())) {
            logger.info(child.getType() + " offspring cannot be resized"
                    + (config.useScaleAttribute() ? " (no scale attribute)" : "")
                    + " and has no baby form; it spawns at normal size.");
        }
    }

    private Location findSpot(World world, Location origin, double angle, double spacing,
                              double halfWidth, double height) {
        for (double factor : new double[] {1.0, 0.5}) {
            double x = origin.getX() + Math.cos(angle) * spacing * factor;
            double z = origin.getZ() + Math.sin(angle) * spacing * factor;
            if (!world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                continue;
            }
            double y = origin.getY();
            // Lift the box slightly so the floor the mob stands on is not counted as a collision.
            BoundingBox box = new BoundingBox(x - halfWidth, y + 0.01, z - halfWidth,
                    x + halfWidth, y + 0.01 + height, z + halfWidth);
            if (!world.hasCollisionsIn(box)) {
                return new Location(world, x, y, z, randomYaw(), 0f);
            }
        }
        // Fallback: the parent's own position, which was valid a moment ago.
        Location fallback = origin.clone();
        fallback.setYaw(randomYaw());
        fallback.setPitch(0f);
        return fallback;
    }

    private double readOriginScale(Mob parent, int parentStage) {
        if (parentStage == SplitRules.ORIGINAL_STAGE) {
            return currentScale(parent);
        }
        Double stored = parent.getPersistentDataContainer().get(keys.originScale(), PersistentDataType.DOUBLE);
        return stored != null && Double.isFinite(stored) && stored > 0 ? stored : 1.0;
    }

    private static double currentScale(Mob mob) {
        AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
        return scale != null ? scale.getBaseValue() : 1.0;
    }

    private static void inheritTarget(Mob parent, Mob child) {
        if (!(child instanceof Enemy)) {
            return;
        }
        LivingEntity target = parent.getTarget();
        if (target == null || !target.isValid()) {
            target = parent.getKiller();
        }
        if (target != null && target.isValid() && !target.isDead()) {
            child.setTarget(target);
        }
    }

    private static float randomYaw() {
        return ThreadLocalRandom.current().nextFloat() * 360f - 180f;
    }
}
