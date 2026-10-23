package io.github.whismerr.mobsplitting;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Salmon;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

/**
 * Copies safe, visible properties from a dying parent to a freshly created offspring.
 *
 * <p>Deliberately NOT copied: inventories (donkey chests), saddles, body armour, villager
 * trades and health. Copying those would duplicate items or produce odd mobs. Every section is
 * guarded so that a problem with one unusual mob never prevents the offspring from spawning.
 */
final class PropertyCopier {

    /** Equipment slots that are copied. Body and saddle slots are skipped to avoid duplicating gear. */
    private static final EquipmentSlot[] COPIED_SLOTS = {
        EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private PropertyCopier() {
    }

    static void copy(Mob parent, Mob child, SplitConfig config, Logger logger) {
        guarded(logger, child, "name", () -> {
            if (config.copyNames() && parent.customName() != null) {
                child.customName(parent.customName());
                child.setCustomNameVisible(parent.isCustomNameVisible());
            }
            child.setSilent(parent.isSilent());
        });
        guarded(logger, child, "age", () -> {
            if (parent instanceof Ageable p && child instanceof Ageable c && !p.isAdult()) {
                c.setBaby();
            }
        });
        guarded(logger, child, "variant", () -> copyVariant(parent, child));
        guarded(logger, child, "owner", () -> {
            if (parent instanceof Tameable p && child instanceof Tameable c && p.isTamed()) {
                c.setTamed(true);
                c.setOwner(p.getOwner());
            }
        });
        if (config.copyEquipment()) {
            guarded(logger, child, "equipment", () -> copyEquipment(parent, child));
        }
    }

    private static void copyVariant(Mob parent, Mob child) {
        // Parent and child always have the same EntityType, so matching interfaces line up.
        if (parent instanceof Colorable p && child instanceof Colorable c) {
            c.setColor(p.getColor()); // sheep wool, shulker colour
        }
        if (parent instanceof Cat p && child instanceof Cat c) {
            c.setCatType(p.getCatType());
        }
        if (parent instanceof Wolf p && child instanceof Wolf c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Frog p && child instanceof Frog c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Axolotl p && child instanceof Axolotl c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Parrot p && child instanceof Parrot c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Rabbit p && child instanceof Rabbit c) {
            c.setRabbitType(p.getRabbitType());
        }
        if (parent instanceof Fox p && child instanceof Fox c) {
            c.setFoxType(p.getFoxType());
        }
        if (parent instanceof MushroomCow p && child instanceof MushroomCow c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Cow p && child instanceof Cow c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Pig p && child instanceof Pig c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Chicken p && child instanceof Chicken c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Llama p && child instanceof Llama c) {
            c.setColor(p.getColor());
        }
        if (parent instanceof Horse p && child instanceof Horse c) {
            c.setColor(p.getColor());
            c.setStyle(p.getStyle());
        }
        if (parent instanceof Panda p && child instanceof Panda c) {
            c.setMainGene(p.getMainGene());
            c.setHiddenGene(p.getHiddenGene());
        }
        if (parent instanceof TropicalFish p && child instanceof TropicalFish c) {
            c.setPattern(p.getPattern());
            c.setBodyColor(p.getBodyColor());
            c.setPatternColor(p.getPatternColor());
        }
        if (parent instanceof Salmon p && child instanceof Salmon c) {
            c.setVariant(p.getVariant());
        }
        if (parent instanceof Villager p && child instanceof Villager c) {
            c.setVillagerType(p.getVillagerType()); // biome look only; trades are not copied
        }
        if (parent instanceof ZombieVillager p && child instanceof ZombieVillager c) {
            c.setVillagerType(p.getVillagerType());
            c.setVillagerProfession(p.getVillagerProfession());
        }
        if (parent instanceof Creeper p && child instanceof Creeper c) {
            c.setPowered(p.isPowered());
        }
        if (parent instanceof Slime p && child instanceof Slime c) {
            c.setSize(p.getSize()); // also covers magma cubes
        }
        if (parent instanceof Phantom p && child instanceof Phantom c) {
            c.setSize(p.getSize());
        }
        if (parent instanceof Goat p && child instanceof Goat c) {
            c.setScreaming(p.isScreaming());
        }
    }

    private static void copyEquipment(Mob parent, Mob child) {
        EntityEquipment from = parent.getEquipment();
        EntityEquipment to = child.getEquipment();
        for (EquipmentSlot slot : COPIED_SLOTS) {
            if (!child.canUseEquipmentSlot(slot)) {
                continue;
            }
            ItemStack item = from.getItem(slot);
            if (item.isEmpty()) {
                continue;
            }
            to.setItem(slot, item.clone());
            // Copies must never drop, otherwise killing offspring would duplicate the parent's gear.
            to.setDropChance(slot, 0.0f);
        }
    }

    private static void guarded(Logger logger, Mob child, String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not copy " + what + " to " + child.getType(), e);
        }
    }
}
