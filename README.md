# MobSplitting

A Paper plugin for Minecraft Java Edition **1.21.8**. When a mob dies it splits into two
smaller copies of itself, and each of those splits once more:

```
1 original (stage 1, normal size, health and speed)
└── dies → 2 stage-2 mobs (65% size, 50% max health, 125% speed)
    └── each dies → 2 stage-3 mobs (40% size, 25% max health, 150% speed) → never split
```

Killing one original mob means killing 7 mobs in total: 1 + 2 + 4.

## Requirements

- A **Paper** server for Minecraft **1.21.8**. Spigot and CraftBukkit are not supported.
- **Java 21**.
- No client mods. Players use the normal Minecraft client.

## Installation

1. Build the plugin (see *Building*) or take the existing JAR from `build/libs/MobSplitting-1.0.0.jar`.
2. Copy the JAR into your server's `plugins` folder.
3. Start or restart the server. Don't use `/reload`.
4. The server creates `plugins/MobSplitting/config.yml`. Edit it, then run `/mobsplitting reload`.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/mobsplitting info` (alias `/msplit`) | `mobsplitting.admin` (ops) | Shows settings and split statistics |
| `/mobsplitting reload` | `mobsplitting.admin` (ops) | Reloads `config.yml` |

## Configuration

Every option is explained in comments inside `config.yml`. The main ones:

| Option | Default | Meaning |
|---|---|---|
| `scale.stage-2` / `scale.stage-3` | `0.65` / `0.4` | Size relative to the **original** mob |
| `scale.use-scale-attribute` | `true` | Shrink mobs with the vanilla `minecraft:scale` attribute |
| `scale.baby-fallback` | `true` | Spawn babies when real scaling is unavailable |
| `excluded-mobs` | bosses, Warden, villagers, slimes | Mob types that never split |
| `excluded-worlds` | `[]` | Worlds with no splitting |
| `excluded-spawn-reasons` | `[]` | For example `SPAWNER` to stop mob-farm mobs from splitting |
| `split-tamed-pets` | `false` | Whether tamed pets split |
| `require-player-kill` | `false` | Only split when a player kills the mob |
| `performance.*` | 80 mobs / 16 blocks / 20 per tick | Anti-lag limits |

## How it works

- **Generation tracking.** Each offspring gets a `mobsplitting:stage` value (2 or 3) in its
  PersistentDataContainer before it enters the world. Mobs without the value are originals
  (stage 1). This data is saved with the entity, so a stage-2 mob that survives a server
  restart still splits only into stage 3. A mob that splits is also flagged
  `mobsplitting:split_done`, so a repeated death event can never split it twice. An invalid or
  corrupt stage value counts as the final stage, so it can never cause extra splits.
- **Real scaling.** In 1.21.8 every living entity has the `minecraft:scale` attribute
  (`Attribute.SCALE` in the Paper API). The plugin sets it so the model **and** hitbox shrink.
  Stage 3 is measured against the original, so an original that was already scaled to 2.0
  produces 1.3 then 0.8. Values are clamped to Minecraft's allowed range of 0.0625 to 16.
- **Health and speed.** Each generation has half its parent's maximum health: stage 2 gets 50%
  and stage 3 gets 25% of the original's maximum health (its real value, including modifiers,
  so a 20-health zombie gives 10 then 5, and a 100-health iron golem gives 50 then 25).
  Offspring spawn at full health for their new maximum. Base movement speed is 125% (stage 2)
  and 150% (stage 3) of the original's base speed. Both are calculated from the original's
  values, which are saved on each offspring as `mobsplitting:origin_max_health` and
  `mobsplitting:origin_speed`, so speed never compounds (1.5x, not 1.25 × 1.25). They're set
  once, as absolute base values, when the offspring spawns. Chunk unloads and restarts just
  restore the saved attributes, so nothing is multiplied twice. Other attribute modifiers, such
  as the baby speed bonus, are left alone. Values are clamped to Minecraft's limits (max health
  at least 1). Flying speed isn't changed.
- **Fallbacks.** If a mob has no scale attribute, or you disable it, the plugin spawns a baby
  for mobs that have one. Otherwise the offspring keeps normal size and the server log says so
  once per mob type. The plugin never claims a mob shrank when it didn't.
- **Copied from the parent:** custom name, silent flag, baby state, colours and variants (sheep,
  cats, wolves, frogs, axolotls, parrots, rabbits, foxes, mooshrooms, cows, pigs, chickens,
  llamas, horses, pandas, tropical fish, salmon, villager biome type, zombie villager
  profession), charged creepers, slime and phantom size, screaming goats, pet owner (when pets
  may split), and held items and armour.
- **Not copied:** chest contents, saddles, horse, wolf and llama body armour, villager trades,
  and current health. Offspring spawn at full health for their reduced maximum.
- **Spawning.** The two offspring appear on opposite sides of the parent, at a random angle. If
  a spot is inside a block, the plugin tries a closer spot, then the parent's own position.
  Vanilla spawn randomisation is turned off, so there are no surprise chicken jockeys or
  random gear.

## Loot and XP

The plugin **never changes drops or XP**. Every mob at every stage, including the original
that splits, drops its normal loot and experience, and offspring are ordinary killable mobs.
Because one original produces 6 more mobs, **total loot and XP per original can be up to
7 times higher**. Keep this in mind for farms. `excluded-spawn-reasons: [SPAWNER]` is a good
way to limit it.

Two vanilla rules still apply:

- Mobs only drop XP and rare loot when a player killed them recently. Offspring killed by
  lava or fall damage drop nothing extra, just like normal mobs.
- Copied equipment has a **0% drop chance**. A stage-2 skeleton's bow is a copy, so it never
  drops, and items can't be duplicated. The original drops its own gear normally.

## Limitations

- Bosses (Ender Dragon, Wither) and mini-bosses (Elder Guardian, Warden) are excluded by
  default. You *can* remove them from `excluded-mobs`, but a spawned dragon outside the
  dragon fight behaves oddly. This isn't recommended.
- Slimes and magma cubes already split in vanilla, so they're excluded by default. If you
  enable them, vanilla splitting and this plugin's splitting both happen.
- Players, armour stands, non-living entities and plugin NPCs (entities with `NPC` metadata,
  such as Citizens) are always excluded.
- Mobs created by vanilla mechanics, such as zombie reinforcements or a spider jockey's
  skeleton, are new originals and can split too.
- Offspring have less health and more speed (see *How it works*), but their normal attack
  damage.
- Offspring created by an older version of this plugin have no saved original values. When
  they split, their own unchanged health and speed count as the original's.
- Mobs that die below the world's minimum height (the void) don't split.
- A mob type that throws an error when spawned is logged once and stops splitting until the
  server restarts.
- The plugin has only been compiled against 1.21.8. Other versions log a warning at startup.

## Building

```
gradlew build        # Windows: .\gradlew.bat build
```

This produces `build/libs/MobSplitting-1.0.0.jar` and runs the unit tests in
`src/test/java`. The tests cover the generation rules: 1 → 2 → 4 and then stop, corrupt
markers, scale maths, and the health and speed maths for stages 2 and 3. They don't need a server.

## Manual in-game test checklist

Run these on a local test server, not your main world.

1. `/summon zombie ~ ~ ~5`, then kill it. **Expect:** 2 smaller zombies (65%).
2. Kill one of them. **Expect:** 2 even smaller zombies (40%).
   Check health and speed with `/attribute @e[type=zombie,limit=1,sort=nearest] minecraft:max_health get`
   and `... minecraft:movement_speed base get`. **Expect:** stage 2 = 10 and 0.2875, stage 3 = 5 and 0.345.
3. Kill a stage-3 zombie. **Expect:** nothing spawns.
4. Kill a named sheep with coloured wool. **Expect:** the offspring keep the name and colour.
5. Kill a skeleton. **Expect:** the offspring hold bows, and killing them drops bones and
   arrows but never a bow.
6. Leave a stage-2 mob alive, run `/stop`, start the server again and kill it.
   **Expect:** it splits into stage 3, not stage 2.
7. Set `debug: true`, run `/mobsplitting reload` and watch the console as you kill mobs.
8. Kill an excluded mob, such as a villager. **Expect:** no split.
