package io.github.whismerr.mobsplitting;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Splits mobs when they die.
 *
 * <p>Loot and XP: this listener never reads or changes {@link EntityDeathEvent#getDrops()} or
 * {@link EntityDeathEvent#getDroppedExp()}. Every mob, at every stage, drops exactly what it
 * normally would.
 */
final class MobSplitListener implements Listener {

    private final MobSplittingPlugin plugin;
    private final SplitKeys keys;
    private final OffspringSpawner spawner;

    private int budgetTick = Integer.MIN_VALUE;
    private int splitsThisTick;

    MobSplitListener(MobSplittingPlugin plugin, SplitKeys keys, OffspringSpawner spawner) {
        this.plugin = plugin;
        this.keys = keys;
        this.spawner = spawner;
    }

    // MONITOR + ignoreCancelled: only react once the death is final (no plugin cancelled it).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        SplitConfig config = plugin.splitConfig();
        if (!config.enabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        // Mob excludes players, armour stands and every non-AI living entity.
        if (!(entity instanceof Mob mob)) {
            return;
        }

        PersistentDataContainer data = mob.getPersistentDataContainer();
        // Guard against the same death being handled twice.
        if (data.has(keys.splitDone(), PersistentDataType.BYTE)) {
            return;
        }
        int stage = SplitRules.resolveStage(data.get(keys.stage(), PersistentDataType.INTEGER));
        OptionalInt childStage = SplitRules.childStage(stage);
        if (childStage.isEmpty()) {
            return; // final stage
        }

        String skipReason = skipReason(mob, stage, config);
        if (skipReason != null) {
            debug(config, () -> "Not splitting " + describe(mob, stage) + ": " + skipReason);
            return;
        }

        // Mark before spawning, so nothing triggered during spawning can split this mob again.
        data.set(keys.splitDone(), PersistentDataType.BYTE, (byte) 1);
        splitsThisTick++;

        int spawned = spawner.spawnOffspring(mob, stage, childStage.getAsInt(), config);
        plugin.recordSplit(spawned);
        debug(config, () -> describe(mob, stage) + " split into " + spawned + " stage-" + childStage.getAsInt() + " offspring");
    }

    /** Returns why a mob must not split, or {@code null} if it may. */
    private String skipReason(Mob mob, int stage, SplitConfig config) {
        if (mob.hasMetadata("NPC")) {
            return "plugin NPC";
        }
        if (config.excludedTypes().contains(mob.getType())) {
            return "type is in excluded-mobs";
        }
        if (!mob.getType().isSpawnable() || mob.getType().getEntityClass() == null
                || spawner.isKnownUnspawnable(mob.getType())) {
            return "type cannot be spawned";
        }
        if (config.excludedWorlds().contains(mob.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return "world is in excluded-worlds";
        }
        if (stage == SplitRules.ORIGINAL_STAGE
                && config.excludedSpawnReasons().contains(mob.getEntitySpawnReason())) {
            return "spawn reason " + mob.getEntitySpawnReason() + " is excluded";
        }
        if (!config.splitTamedPets() && mob instanceof Tameable tameable && tameable.isTamed()) {
            return "tamed pet";
        }
        if (config.requirePlayerKill() && mob.getKiller() == null) {
            return "not killed by a player";
        }

        int tick = Bukkit.getCurrentTick();
        if (tick != budgetTick) {
            budgetTick = tick;
            splitsThisTick = 0;
        }
        if (splitsThisTick >= config.maxSplitsPerTick()) {
            return "max-splits-per-tick reached";
        }
        int nearby = mob.getWorld().getNearbyEntitiesByType(Mob.class, mob.getLocation(), config.nearbyRadius()).size();
        if (nearby > config.maxNearbyMobs()) {
            return nearby + " mobs nearby (max-nearby-mobs is " + config.maxNearbyMobs() + ")";
        }
        return null;
    }

    /** Logs only when debug is on; the message is built lazily so normal play pays nothing for it. */
    private void debug(SplitConfig config, Supplier<String> message) {
        if (config.debug()) {
            plugin.getLogger().info("[debug] " + message.get());
        }
    }

    private static String describe(Mob mob, int stage) {
        return mob.getType() + " (stage " + stage + ") at " + mob.getWorld().getName() + " "
                + mob.getLocation().getBlockX() + "," + mob.getLocation().getBlockY() + "," + mob.getLocation().getBlockZ();
    }
}
