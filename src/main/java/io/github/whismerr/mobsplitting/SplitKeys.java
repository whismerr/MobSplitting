package io.github.whismerr.mobsplitting;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Keys for the persistent data stored on entities. Persistent data is saved with the
 * entity, so stages survive chunk unloads and server restarts.
 */
public final class SplitKeys {

    /** Integer stage (2 or 3). Absent on original mobs. */
    private final NamespacedKey stage;
    /** Double scale of the original stage-1 mob, so stage 3 is sized relative to it. */
    private final NamespacedKey originScale;
    /** Double maximum health of the original stage-1 mob, so every stage is sized relative to it. */
    private final NamespacedKey originMaxHealth;
    /** Double base movement speed of the original stage-1 mob, so speed bonuses never compound. */
    private final NamespacedKey originSpeed;
    /** Byte flag set on a mob as soon as it splits, so it can never split twice. */
    private final NamespacedKey splitDone;

    public SplitKeys(Plugin plugin) {
        this.stage = new NamespacedKey(plugin, "stage");
        this.originScale = new NamespacedKey(plugin, "origin_scale");
        this.originMaxHealth = new NamespacedKey(plugin, "origin_max_health");
        this.originSpeed = new NamespacedKey(plugin, "origin_speed");
        this.splitDone = new NamespacedKey(plugin, "split_done");
    }

    public NamespacedKey stage() {
        return stage;
    }

    public NamespacedKey originScale() {
        return originScale;
    }

    public NamespacedKey originMaxHealth() {
        return originMaxHealth;
    }

    public NamespacedKey originSpeed() {
        return originSpeed;
    }

    public NamespacedKey splitDone() {
        return splitDone;
    }
}
