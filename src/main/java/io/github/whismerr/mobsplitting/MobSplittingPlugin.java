package io.github.whismerr.mobsplitting;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MobSplitting: when an original mob dies it splits into two smaller copies, each of which
 * splits once more into two even smaller copies. Final-stage mobs do not split.
 */
public final class MobSplittingPlugin extends JavaPlugin {

    private static final String TARGET_MINECRAFT_VERSION = "1.21.8";

    private SplitConfig splitConfig;
    private long splitCount;
    private long offspringCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSplitConfig();

        SplitKeys keys = new SplitKeys(this);
        OffspringSpawner spawner = new OffspringSpawner(getLogger(), keys);
        getServer().getPluginManager().registerEvents(new MobSplitListener(this, keys, spawner), this);

        PluginCommand command = getCommand("mobsplitting");
        if (command != null) {
            MobSplittingCommand executor = new MobSplittingCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        logStartup();
    }

    @Override
    public void onDisable() {
        getLogger().info("MobSplitting disabled. Splits this session: " + splitCount
                + ", offspring spawned: " + offspringCount + ".");
    }

    /** Reloads config.yml from disk. */
    public void reloadSplitConfig() {
        reloadConfig();
        splitConfig = SplitConfig.load(getConfig(), getLogger());
    }

    public SplitConfig splitConfig() {
        return splitConfig;
    }

    void recordSplit(int offspring) {
        splitCount++;
        offspringCount += offspring;
    }

    long splitCount() {
        return splitCount;
    }

    long offspringCount() {
        return offspringCount;
    }

    private void logStartup() {
        String mcVersion = getServer().getMinecraftVersion();
        getLogger().info("MobSplitting " + getPluginMeta().getVersion() + " enabled on Minecraft " + mcVersion + ".");
        if (!TARGET_MINECRAFT_VERSION.equals(mcVersion)) {
            getLogger().warning("This build targets Minecraft " + TARGET_MINECRAFT_VERSION
                    + ". It may still work on " + mcVersion + ", but that has not been tested.");
        }

        int eligible = 0;
        for (EntityType type : EntityType.values()) {
            Class<? extends Entity> entityClass = type.getEntityClass();
            if (type.isSpawnable() && entityClass != null && Mob.class.isAssignableFrom(entityClass)
                    && !splitConfig.excludedTypes().contains(type)) {
                eligible++;
            }
        }
        getLogger().info(eligible + " mob types can split; " + splitConfig.excludedTypes().size()
                + " excluded by config. Scales: stage 2 = " + splitConfig.stage2Scale()
                + ", stage 3 = " + splitConfig.stage3Scale()
                + (splitConfig.useScaleAttribute() ? " (minecraft:scale attribute)" : " (scale attribute disabled)") + ".");
        if (!splitConfig.enabled()) {
            getLogger().warning("Splitting is disabled in config.yml (enabled: false).");
        }
    }
}
