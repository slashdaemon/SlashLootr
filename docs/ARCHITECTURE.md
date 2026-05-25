# SlashLootr Architecture

A technical deep-dive into how SlashLootr delivers per-player loot for naturally-generated containers **without** registering custom blocks and **without** requiring a client install. This document is the canonical reference for the mod's design, the vanilla code paths it hooks, and the per-band rewrites that absorb Mojang's API drift between MC 1.20.1 and 1.21.9.

If you only want to use the mod, [`README.md`](../README.md) is enough. This document is for contributors and for the next person who has to add an MC version.

---

## Table of contents

- [1. Overview & motivation](#1-overview--motivation)
- [2. The vanilla loot path (what we hook)](#2-the-vanilla-loot-path-what-we-hook)
- [3. The intercept layer](#3-the-intercept-layer)
- [4. Per-player view: rolling, seeding, presenting](#4-per-player-view-rolling-seeding-presenting)
- [5. Identity & keying](#5-identity--keying)
- [6. Persistence](#6-persistence)
- [7. Configuration & admin commands](#7-configuration--admin-commands)
- [8. Project layout](#8-project-layout)
- [9. Per-band specifics](#9-per-band-specifics)
- [10. Comparison vs. Lootr / myLoot / LootrPlugin](#10-comparison-vs-lootr--myloot--lootrplugin)
- [11. Limitations and rationale](#11-limitations-and-rationale)
- [12. Future extensions](#12-future-extensions)
- [13. Adding a new band](#13-adding-a-new-band)

---

## 1. Overview & motivation

### The problem

Vanilla Minecraft generates loot containers (chests in mineshafts, barrels in villages, shulker boxes in ancient cities, chest minecarts on rails, chest boats in shipwrecks…) with a **`LootTable` NBT tag** and a **`LootTableSeed`**. The first time anyone opens one, vanilla rolls the loot table into the container's inventory and clears the tag. Whoever's there first takes everything; everyone else opens an empty chest.

On a multiplayer server, this is a race. The mod [Lootr](https://github.com/LootrMinecraft/Lootr) solves it by giving each player their own copy of the loot — but it does so by **registering custom block variants** (`LootrChestBlock`, `LootrBarrelBlock`, etc.) and swapping the vanilla block-state at the container's position. That swap:

- requires the mod on every client (vanilla clients see an unknown block and refuse to connect);
- breaks scoreboard selectors, datapack predicates, `/data` queries, structure saves, and any mod that scans for `minecraft:chest`;
- the Lootr maintainers themselves [publicly invite](https://modrinth.com/mod/lootr) plugin developers to write a server-only Fabric alternative — none has existed until now.

### The insight

The custom block is **not load-bearing**. Lootr uses it for three things:

1. Routing the player's right-click to its own handler (`useWithoutItem` on the custom block).
2. The gold→blue "looted" visual indicator.
3. Stashing some per-container state on the block entity.

None of those are required to deliver the actual user value. The real mechanic — "every player rolls the loot table once, sees their own inventory, server persists it" — is **pure server-side bookkeeping**. We can deliver it by intercepting two vanilla code paths:

1. **Cancel the vanilla loot roll** so the world's container never gets baked with loot.
2. **Substitute the menu** when a player opens it, presenting a per-player inventory backed by a fresh roll.

Both can be done without ever modifying world block-state. The chest stays a `minecraft:chest` with its `LootTable` NBT tag intact forever. Vanilla clients see a regular chest UI populated with whatever items the server syncs to their menu — exactly the same wire format as a normal chest. **No client install required.**

### Outcome

A Fabric server-side mod that delivers Lootr's headline feature across 8 Minecraft versions (1.20.1 → 1.21.9) with zero client install requirement and zero vanilla block-state changes.

---

## 2. The vanilla loot path (what we hook)

### Block containers

The relevant vanilla class is `RandomizableContainerBlockEntity` (the abstract superclass of `ChestBlockEntity`, `BarrelBlockEntity`, `ShulkerBoxBlockEntity`, `TrappedChestBlockEntity`, etc.). Since MC 1.20.5 it also implements the `RandomizableContainer` interface.

The flow in vanilla:

```
Player right-clicks a chest
    ↓
ChestBlock#useWithoutItem  (or BarrelBlock#useWithoutItem, etc.)
    ↓
BaseContainerBlockEntity#getMenuProvider  →  returns this BlockEntity as the MenuProvider
    ↓
ServerPlayer#openMenu(provider)
    ↓
provider.createMenu(...)  →  calls into ChestMenu / BarrelMenu / etc.
    ↓
The menu reads inventory contents via Container#getItem(slot)
    ↓
RandomizableContainerBlockEntity#getItem(slot)  →  calls #unpackLootTable(player) FIRST
    ↓
unpackLootTable:
    if (lootTable != null) {
        loot = registry.getLootTable(lootTable);
        setLootTable(null);                          ← CLEARS THE TAG
        params = new LootParams.Builder(level).withParameter(ORIGIN, pos)...
        loot.fill(this, params, lootTableSeed);    ← BAKES INTO THIS BE
    }
```

The key insight: **`unpackLootTable` is called lazily on first inventory read**. It (1) reads the loot table NBT, (2) clears it, (3) rolls items into the world's container.

If we **cancel** `unpackLootTable` server-side:
- The loot is never rolled into the world container.
- The `LootTable` NBT tag is never cleared.
- Every subsequent open re-triggers the same flow — and we cancel again.
- The container is **permanently "unrolled" from vanilla's perspective**.

### Entity containers

The same lazy-roll pattern exists for chest minecarts, hopper minecarts, and chest boats. Since MC 1.20.5 these all implement `ContainerEntity` (interface) and the relevant method is `ContainerEntity#unpackChestVehicleLootTable(Player)`. In MC 1.20.1 there's no shared interface — `AbstractMinecartContainer` and `ChestBoat` each have their own `unpackLootTable(Player)` method.

---

## 3. The intercept layer

### Two hooks, both server-side

```
                  ┌────────────────────────────────────────┐
                  │  PLAYER RIGHT-CLICKS A CONTAINER       │
                  └────────────────────────────────────────┘
                                    │
                                    ▼
         ┌──────────────────────────────────────────────────────┐
         │  Hook 1: Fabric UseBlockCallback / UseEntityCallback │
         │  (the EARLIEST event we can register against)        │
         └──────────────────────────────────────────────────────┘
                                    │
                ┌───────────────────┴───────────────────┐
                ▼                                       ▼
       Container has LootTable                Container has no LootTable
       NBT tag (naturally gen'd)              (player-placed)
                │                                       │
                ▼                                       ▼
   Build per-player SimpleContainer          Return InteractionResult.PASS
   (rolled or loaded from SavedData)         → vanilla handles normally
                │
                ▼
   serverPlayer.openMenu(
       new SimpleMenuProvider(
           (id, inv, p) -> ChestMenu.threeRows(id, inv, perPlayerContainer),
           Component.translatable("container.chest")
       )
   )
                │
                ▼
   Return InteractionResult.SUCCESS  ← short-circuits the vanilla open path

   ════════════════════════════════════════════════════════════════════

   Meanwhile, if anything in vanilla EVER reaches a path that would call
   unpackLootTable on the world container...

         ┌──────────────────────────────────────────────────────┐
         │  Hook 2: Mixin on unpackLootTable                    │
         │  @Inject(at = HEAD, cancellable = true)              │
         │  → ci.cancel() if server-side AND lootTable != null  │
         └──────────────────────────────────────────────────────┘

   The world container is NEVER baked with loot. The LootTable NBT tag is
   NEVER cleared. The container stays "naturally generated forever."
```

### Why both hooks are necessary

Hook 1 alone is *almost* enough — but vanilla can call `unpackLootTable` from paths we don't intercept (hopper pulls, command-block `/data merge`, mod interactions). Hook 2 is the safety net: even if some other code path triggers a vanilla read, the loot still never bakes.

Hook 2 alone is **not** enough — if we cancel `unpackLootTable` but don't substitute the menu, the player opens an empty chest and we don't serve their per-player loot at all.

### Hook 1 in code

```java
// versions/1.21.1/.../handler/ContainerInteractionHandler.java
public class ContainerInteractionHandler implements UseBlockCallback {
    @Override
    public InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;

        ServerLevel level = (ServerLevel) world;
        if (SlashLootrConfig.get().isDimensionBlocked(level.dimension().location())) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainer rc)) return InteractionResult.PASS;
        ResourceKey<LootTable> table = rc.getLootTable();
        if (table == null) return InteractionResult.PASS;                  // Player-placed: pass through
        if (SlashLootrConfig.get().isLootTableBlocked(table.location())) return InteractionResult.PASS;
        // ... build per-player container and openMenu
    }
}
```

### Hook 2 in code

```java
// versions/1.21.1/.../mixin/MixinRandomizableContainer.java
@Mixin(RandomizableContainer.class)
public interface MixinRandomizableContainer {
    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    default void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        RandomizableContainer self = (RandomizableContainer) this;
        if (!(self instanceof BlockEntity be)) return;
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        if (self.getLootTable() == null) return;
        ci.cancel();
    }
}
```

The entity-container equivalent (`MixinContainerEntity`) hooks `unpackChestVehicleLootTable` with the same shape.

---

## 4. Per-player view: rolling, seeding, presenting

### Building the per-player container

When the use-callback fires for a naturally-generated container:

1. Look up the per-dimension `SlashLootrState` (the persistent store).
2. Look up the `PlayerLootEntry` for this container's identity (block pos or entity UUID).
3. Look up the `SimpleContainer` for this player's UUID.
4. **If absent**: roll a fresh one via `LootTable#fill` using a player-derived seed, register a `ContainerListener` that marks the state dirty on any slot change, store it.
5. Open a vanilla `ChestMenu` (or `ShulkerBoxMenu` / `HopperMenu`) backed by that container.

### Seed derivation

```java
// common/.../SeedDeriver.java
public static long derive(long containerSeed, UUID player) {
    return containerSeed
         ^ player.getMostSignificantBits()
         ^ Long.rotateLeft(player.getLeastSignificantBits(), 17);
}
```

Properties:
- **Stable**: same player + same container = same loot. If a player closes their menu with items still inside, the same items are still there on re-open (because we persist; the seed determinism is a back-stop, not the primary mechanism).
- **Differentiated**: two different players get materially different loot from the same container.

### Menu types

`ContainerKind` enum maps each container type to its slot count, display name, and `MenuProvider` factory:

```java
public enum ContainerKind {
    CHEST           (27, "container.chest",      false),
    BARREL          (27, "container.barrel",     false),
    SHULKER         (27, "container.shulkerBox", true),   // → ShulkerBoxMenu
    DOUBLE_CHEST    (54, "container.chestDouble",false),  // → ChestMenu.sixRows
    MINECART_CHEST  (27, "container.minecart",   false),
    MINECART_HOPPER ( 5, "container.hopper",     false),  // → HopperMenu
    CHEST_BOAT      (27, "container.chestBoat",  false);
    // ...
    public MenuProvider menuProvider(Container container) { ... }
}
```

### Server-only client compatibility

Vanilla clients consume `ScreenHandler.sendAllDataToRemote()` sync packets and slot updates. They don't know — and don't need to know — what `Container` backs the server's `Menu`. As long as the server sends them slot contents, the client renders the UI and echoes clicks back to the server. The server validates clicks against its `Container`. Our `SimpleContainer` Just Works.

---

## 5. Identity & keying

| Container kind                                | Identity key                                  | Persists across      |
| --------------------------------------------- | --------------------------------------------- | -------------------- |
| Single chest / trapped chest / barrel         | `(ResourceKey<Level>, BlockPos)`              | Chunk reload, restart |
| Shulker box                                   | `(ResourceKey<Level>, BlockPos)`              | Chunk reload, restart |
| Double chest                                  | Each half keyed independently by its own `BlockPos`; combined view via `CompoundContainer` | Same |
| Chest minecart / hopper minecart / chest boat | `(ResourceKey<Level>, entity.UUID)`           | Entity NBT persistence |

### Why `(dim, BlockPos)` is safe

- Chunk reload preserves block-entity NBT, so the same `BlockPos` always refers to the same naturally-generated container (the `LootTable` tag remains).
- If a player **breaks** a naturally-generated chest and **places** a new one at the same position, the new chest has no `LootTable` NBT tag (player-placed chests don't get loot tables). Our use-callback checks for the tag and returns `PASS`, so the player-placed chest behaves vanilla.
- We never store the loot table or seed in our `SavedData` — we read them from the live BlockEntity at intercept time. The BE always has them (we cancel the only path that would clear them).

### Double-chest handling

Two adjacent `ChestBlockEntity`s with the same `ChestType.LEFT` / `RIGHT` form one logical 54-slot inventory. We treat each half independently:

1. On right-click, detect the pair via `ChestBlock.getConnectedDirection(state)`.
2. Canonicalize ordering by packed `BlockPos`: lower-packed is "first", higher is "second" — deterministic regardless of which half the player clicked.
3. Look up (or roll) per-player loot for **both halves separately** — each half rolls its own loot table independently, matching vanilla semantics.
4. Wrap both `SimpleContainer`s in a vanilla `CompoundContainer(first, second)` and open `ChestMenu.sixRows`.

Breaking one half leaves the other half's per-player data intact (since they're stored under different `BlockPos` keys).

### Entity identity

For chest minecarts and chest boats, `entity.getUUID()` is stable across chunk unload/reload because Mojang persists it in the entity NBT. Moving the entity around the world doesn't change its UUID.

---

## 6. Persistence

### Where it lives

`world/<dimension>/data/slashlootr.dat` — one per dimension, lazily created on first use, accessed via `ServerLevel#getDataStorage().computeIfAbsent(...)`.

### Schema (Bands A–D: NBT-based)

```
SlashLootrState (CompoundTag)
├── blocks: ListTag<CompoundTag>
│   └── { pos: long, players: ListTag<CompoundTag> {
│             uuid: UUID, size: int, items: ListTag (SimpleContainer.createTag) } }
└── entities: ListTag<CompoundTag>
    └── { uuid: UUID, players: ListTag<CompoundTag> {
              uuid: UUID, size: int, items: ListTag (SimpleContainer.createTag) } }
```

Implementation: see `versions/1.21.1/src/main/java/.../store/SlashLootrState.java` (Bands B–D use the same source via 1.21.1's directory or a copy of it; Band A is a per-band rewrite that drops `HolderLookup.Provider` arguments).

### Schema (Band E: Codec-based)

Bands 1.21.6 and 1.21.9 have to go through `Codec<T>` because `SavedData.Factory` was removed in 1.21.5. Equivalent schema, but expressed declaratively:

```java
record SlotItem(int slot, ItemStack item) {
    static final Codec<SlotItem> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("slot").forGetter(SlotItem::slot),
        ItemStack.CODEC.fieldOf("item").forGetter(SlotItem::item)
    ).apply(i, SlotItem::new));
}

record PlayerSlots(UUID player, int size, List<SlotItem> items) { ... }
record BlockEntryRec(long pos, List<PlayerSlots> players) { ... }
record EntityEntryRec(UUID uuid, List<PlayerSlots> players) { ... }

public static final Codec<SlashLootrState> CODEC = RecordCodecBuilder.create(i -> i.group(
    BlockEntryRec.CODEC.listOf().fieldOf("blocks").forGetter(SlashLootrState::serializeBlocks),
    EntityEntryRec.CODEC.listOf().fieldOf("entities").forGetter(SlashLootrState::serializeEntities)
).apply(i, SlashLootrState::deserialize));

public static final SavedDataType<SlashLootrState> TYPE =
    new SavedDataType<>("slashlootr", SlashLootrState::new, CODEC, null);
```

Note the **sparse** representation: each `PlayerSlots` only stores the slot indices that are non-empty, plus the original container size. Robust against future container-size changes, and avoids encoding 26 empty `ItemStack`s per minimally-looted barrel.

### Dirty marking

Every personal `SimpleContainer` gets a `ContainerListener` that fires on any slot change:

```java
public SimpleContainer wrap(SimpleContainer container) {
    container.addListener(c -> setDirty());
    return container;
}
```

Mojang's `SavedData` framework persists dirty saved data on world save (every ~10s by default, and at shutdown).

---

## 7. Configuration & admin commands

### `config/slashlootr.json`

Created on first launch with defaults. Reloaded only at startup.

```json
{
  "dimensionBlocklist": [],
  "lootTableBlocklist": [],
  "playOpenCloseSounds": true
}
```

| Key                   | Type            | Default | Effect                                                                                    |
| --------------------- | --------------- | ------- | ----------------------------------------------------------------------------------------- |
| `dimensionBlocklist`  | `List<String>`  | `[]`    | Dimensions where SlashLootr is disabled; vanilla loot behavior applies                    |
| `lootTableBlocklist`  | `List<String>`  | `[]`    | Loot tables to skip; the container will be left alone for vanilla to handle               |
| `playOpenCloseSounds` | `boolean`       | `true`  | Play the chest/barrel/shulker open sound when the per-player menu opens                   |

### `/slashlootr` commands

All require permission level 2 (op).

| Command                                | Effect                                                                  |
| -------------------------------------- | ----------------------------------------------------------------------- |
| `/slashlootr forget here`              | Wipe every player's personal loot at the block the operator is looking at |
| `/slashlootr forget at <x> <y> <z>`    | Same, by explicit coordinate                                            |
| `/slashlootr forget player <player>`   | Wipe a player's personal loot at every container in the current dim     |
| `/slashlootr forget all`               | **Not yet implemented.** Currently prints "stop server and delete the .dat file." Adding this requires exposing the internal maps for iteration. |

---

## 8. Project layout

```
SlashLootr/
├── build.gradle               # Root: Loom plugin, buildAll task, per-band JAR aggregation
├── settings.gradle            # Includes :common + 8 version subprojects
├── gradle.properties          # mod_version, maven_group, loader_version (shared across bands)
├── common/                    # Java 17, version-agnostic
│   └── src/main/java/dev/blockacademy/slashlootr/common/
│       └── SeedDeriver.java   # The only thing that's truly portable
└── versions/
    ├── 1.20.1/                # Band A — Java 17, unique source tree
    ├── 1.20.5/                # Band B — uses its own copy of the 1.21.1 baseline
    ├── 1.21/                  # Band C — sourceSet points at versions/1.21.1
    ├── 1.21.1/                # Band C — primary baseline; all source lives here
    ├── 1.21.2/                # Band D — copy of 1.21.1 with getContainerLootTable rename
    ├── 1.21.4/                # Band D — same as 1.21.2
    ├── 1.21.6/                # Band E — copy of 1.21.1 + codec-based store rewrite
    └── 1.21.9/                # Band E — same as 1.21.6
```

### A note on the package name

All bands use the same Java package: `dev.blockacademy.slashlootr.v1_21_1`. This is a deliberate shortcut — bands are separate JARs and never share a classpath, so the package name is just a name. Renaming it per-band would force the `fabric.mod.json` entrypoint and mixin config `package` field to change too. We accept the awkward naming in exchange for not editing 13 files per band.

The package name was set when 1.21.1 was the only band; future cleanup might rename to `dev.blockacademy.slashlootr` (no version suffix), but it's cosmetic.

### Baseline file inventory (`versions/1.21.1/`)

12 source files plus 2 resources:

```
src/main/java/dev/blockacademy/slashlootr/v1_21_1/
├── SlashLootrMod.java                          # ModInitializer; registers callbacks + command
├── command/
│   └── SlashLootrCommand.java                  # /slashlootr forget …
├── config/
│   └── SlashLootrConfig.java                   # config/slashlootr.json
├── core/
│   ├── ContainerKind.java                      # Per-container-type menu factory + slot count
│   ├── LootRoller.java                         # Wraps LootTable#fill with per-player seed
│   └── OpenSoundFx.java                        # Plays the open sound (no lid animation)
├── handler/
│   ├── ContainerInteractionHandler.java        # UseBlockCallback body
│   └── EntityInteractionHandler.java           # UseEntityCallback body
├── mixin/
│   ├── MixinRandomizableContainer.java         # Cancel vanilla unpackLootTable (block)
│   └── MixinContainerEntity.java               # Cancel vanilla unpackChestVehicleLootTable (entity)
└── store/
    ├── PlayerLootEntry.java                    # Per-container Map<UUID, SimpleContainer>
    └── SlashLootrState.java                    # Per-dimension SavedData

src/main/resources/
├── fabric.mod.json                             # Mod metadata
└── slashlootr.mixins.json                      # Mixin manifest
```

---

## 9. Per-band specifics

This is the section that matters when you add a new MC version. For each band, we document:

1. **Why this band exists** — what vanilla API change made it necessary.
2. **Files that diverge** from the 1.21.1 baseline.
3. **Build configuration** — Loom version, Java toolchain, Fabric API version.

### Band C — MC 1.21, 1.21.1 (the baseline)

**Why**: 1.21.1 is The Block Academy's primary server version. 1.21 is included because it shares the same surface; we get it for free.

**Files**: all source lives under `versions/1.21.1/`. The 1.21 subproject's `build.gradle` points at 1.21.1's source directories rather than maintaining a copy:

```groovy
sourceSets.main.java.srcDirs = [project(":versions:1.21.1").file("src/main/java")]
sourceSets.main.resources.srcDirs = [project(":versions:1.21.1").file("src/main/resources")]
```

**Why this works**: 1.21.0 and 1.21.1 are binary-compatible enough that the same compiled mixins and handlers load on both. No API surface difference touches our code paths.

**Build**: Loom 1.11.x, Java 21, Fabric API 0.102.0+1.21 / 0.115.6+1.21.1.

### Band B — MC 1.20.5

**Why**: 1.20.5 was a watershed release. It introduced:
- `RandomizableContainer` **interface** (the previous `RandomizableContainerBlockEntity` class was demoted to a concrete subclass).
- `ContainerEntity` **interface** for chest minecarts, hopper minecarts, and chest boats.
- `ResourceKey<LootTable>` replacing raw `ResourceLocation` for loot table references.
- `HolderLookup.Provider` parameter added to NBT save/load methods on `SavedData`, `SimpleContainer.fromTag`/`createTag`, etc.

**Files that diverge**: **none**. The 1.21.1 baseline source compiles cleanly against 1.20.5 because all the APIs we use were introduced at 1.20.5.

**Build**: 1.20.5 has its own `versions/1.20.5/build.gradle` and a copy of the source tree (not a symlink — symlinks across Gradle subprojects are fragile on Windows). Loom 1.11.x, Java 21, Fabric API 0.97.8+1.20.5.

### Band D — MC 1.21.2, 1.21.4

**Why**: between 1.21.1 and 1.21.2, Mojang renamed the loot-table accessors on `ContainerEntity`:

| Before (1.21, 1.21.1)   | After (1.21.2+)                |
| ----------------------- | ------------------------------ |
| `getLootTable()`        | `getContainerLootTable()`      |
| `setLootTable(...)`     | `setContainerLootTable(...)`   |
| `getLootTableSeed()`    | `getContainerLootTableSeed()`  |
| `setLootTableSeed(...)` | `setContainerLootTableSeed(...)` |

`RandomizableContainer` (block side) is unaffected — that rename only happened on the entity interface.

**Files that diverge** (2):
- `handler/EntityInteractionHandler.java`: two call sites changed.
- `mixin/MixinContainerEntity.java`: one call site changed.

Diff:

```diff
-        ResourceKey<LootTable> table = ce.getLootTable();
+        ResourceKey<LootTable> table = ce.getContainerLootTable();
...
-            container = LootRoller.rollForEntity(level, entity, table, ce.getLootTableSeed(), sp, kind.slots);
+            container = LootRoller.rollForEntity(level, entity, table, ce.getContainerLootTableSeed(), sp, kind.slots);
```

**Build**: Loom 1.11.x, Java 21, Fabric API 0.106.1+1.21.2 / 0.114.0+1.21.4.

### Band F — MC 1.21.11

**Why**: at 1.21.11 Mojang shipped a sweeping rename:

- Vehicle subpackage moves: `net.minecraft.world.entity.vehicle.{ChestBoat,MinecartChest,MinecartHopper,AbstractMinecartContainer}` all moved into `{boat,minecart}` subpackages.
- `ResourceLocation` → `Identifier` (renamed in place under `net.minecraft.resources`).
- `ResourceKey#location()` → `ResourceKey#identifier()`.
- `Level#random` is now protected — must use `Level#getRandom()` accessor.
- `CommandSourceStack#hasPermission(int)` removed; the new pattern is `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` as a predicate.
- `SimpleContainer#addListener` removed entirely — no replacement API. We work around it by subclassing `SimpleContainer` as `DirtyContainer` and overriding `setChanged()` to mark the owning `SavedData` dirty.

**Files that diverge** vs Band E (10 of 12 source files; same shape as Band G's source tree):
- `mixin/MixinRandomizableContainer.java`, `mixin/MixinContainerEntity.java`: import `Identifier` instead of `ResourceLocation`; `.identifier()` instead of `.location()`.
- `handler/ContainerInteractionHandler.java`, `handler/EntityInteractionHandler.java`: vehicle class imports moved to subpackages; same rename treatment for the loot-table type.
- `core/LootRoller.java`: `Identifier` typed loot-table refs; `tableKey.identifier()` for log output.
- `core/OpenSoundFx.java`: `level.getRandom().nextFloat()` instead of `level.random.nextFloat()`.
- `config/SlashLootrConfig.java`: blocklist API takes `Identifier`.
- `command/SlashLootrCommand.java`: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` predicate.
- `store/SlashLootrState.java`: new inner class `DirtyContainer extends SimpleContainer`; `wrap()` now copies the input into a `DirtyContainer` (since there's no `addListener`); `SavedDataType` first arg still `String` (changes to `Identifier` only in 26.1).
- `store/PlayerLootEntry.java`: stores the wrapped `DirtyContainer` returned by `owner.wrap(...)` instead of the original input.

**Build**: standard composite subproject — Loom 1.13.6, Java 21, Mojang mappings, Fabric API 0.141.2+1.21.11.

### Band G — MC 26.1.2 ("Tiny Takeover")

**Why**: 26.1.x ships **unobfuscated** — Mojang names are the runtime class names — and requires JDK 25 + Loom 1.15.5 + Gradle 9.4. Loom 1.15.5 conflicts with the main composite's Loom 1.13.6, so this band lives in a **quarantined** sibling Gradle build at `versions/26.1.2/`. The root `build.gradle` shells out to its wrapper via an `Exec` task (`build26`).

**Files that diverge** vs Band F (4 source files):
- `store/SlashLootrState.java`:
  - `SavedDataType` first-arg type changed from `String` to `Identifier`. `STATE_ID = Identifier.fromNamespaceAndPath("slashlootr", "slashlootr")`.
  - Imports `Identifier` (Band F does not).
- `SeedDeriver.java`: **inlined** into the band's package (`dev.blockacademy.slashlootr.v1_21_1.SeedDeriver`). The quarantine can't reach the main composite's `:common` subproject.
- `core/LootRoller.java`: import updated to the inlined `SeedDeriver` path.
- `fabric.mod.json` / `slashlootr.mixins.json`: same content as Band F; carried in the quarantine for self-containment.

**Build infrastructure**:
- `versions/26.1.2/gradle/wrapper/*` — Gradle 9.4.1 wrapper, cribbed from StreamCraft's 26.1 setup.
- `versions/26.1.2/settings.gradle` — applies `org.gradle.toolchains.foojay-resolver-convention` so Gradle auto-provisions JDK 25 if not already installed locally.
- `versions/26.1.2/build.gradle` — Loom 1.15.5, `release = 25`, no `mappings loom.officialMojangMappings()` (26.1 is unobfuscated).
- Root `build.gradle` adds `tasks.register("build26", Exec)` that runs `versions/26.1.2/gradlew build --no-daemon` and a copy step in `buildAll` that aggregates the quarantined JAR into the shared `build/release/`.

**Build**: quarantined — `cd versions/26.1.2 && ./gradlew build` with `JAVA_HOME` set to a JDK 25 (or let Foojay download one).

### Band E — MC 1.21.6, 1.21.9

**Why**: between 1.21.4 and 1.21.5/1.21.6, Mojang removed `SavedData.Factory<T>` entirely. The new registration path is:

```java
public static final SavedDataType<T> TYPE = new SavedDataType<>(id, ctor, codec, dataFixType);
DimensionDataStorage.computeIfAbsent(SavedDataType<T> type)  // single arg now
```

All serialization has to go through a `Codec<T>`. The override-`save(CompoundTag, HolderLookup.Provider)`-on-the-SavedData-subclass path is gone.

**Files that diverge** (2):
- `store/SlashLootrState.java`: rewritten using `RecordCodecBuilder`. Defines `SlotItem`, `PlayerSlots`, `BlockEntryRec`, `EntityEntryRec` records, each with its own `Codec`, composed into a final `Codec<SlashLootrState>`. Drops the `@Override save(...)` method entirely. `computeIfAbsent` takes only the `SavedDataType<T>`.
- `store/PlayerLootEntry.java`: drops `toNbt()` / `fromNbt(ListTag)` methods (which used `SimpleContainer.fromTag`/`createTag`) in favor of `toPlayerSlotsList()`, which produces records the codec can consume.

Plus the Band D `getContainerLootTable` rename carries through.

**Sparse slot encoding**: `ItemStack.CODEC` throws on empty stacks, so we explicitly skip empty slots and store `(slot, item)` tuples. On load we re-create a `SimpleContainer` of the original size and write items at their indices. Empty slots become empty stacks by default.

**Build**: Loom **1.10+ required** for 1.21.6, **1.11+ required** for 1.21.9. Java 21. Fabric API 0.128.1+1.21.6 / 0.134.1+1.21.9.

### Band A — MC 1.20.1 (the hardest one)

**Why**: 1.20.1 predates all the convenience APIs we built the baseline on. Notable absences:

- **`RandomizableContainer` interface doesn't exist.** Mixin must target the `RandomizableContainerBlockEntity` **class** directly.
- **`ContainerEntity` interface doesn't exist.** Chest minecarts and chest boats each have their own `lootTable` / `lootTableSeed` fields and their own `unpackLootTable(Player)` method — there's no shared abstraction.
- **Loot-table fields are private**, not exposed via accessors. Direct field access fails to compile because the field is `protected` (block side) or `private` (entity side); we need mixin `@Accessor`s.
- **`ResourceLocation` instead of `ResourceKey<LootTable>`** for loot table references.
- **`SavedData.Factory<T>` doesn't exist yet**. The registration path is `DimensionDataStorage.computeIfAbsent(Function<CompoundTag, T> loader, Supplier<T> factory, String id)` — three args, no factory wrapper.
- **`HolderLookup.Provider` doesn't exist** in any of: `SavedData.save(CompoundTag)`, `SimpleContainer.fromTag(ListTag)` / `createTag()`. All NBT methods are single-arg.
- **Java 17 required.** 1.20.1 ships with JDK 17 minimum (vs JDK 21 for 1.20.5+).

**Files that diverge** (10 out of 12 baseline files, plus 4 net-new files):

| File | What's different |
| ---- | ---------------- |
| `mixin/MixinRandomizableContainer.java` | `@Mixin(RandomizableContainerBlockEntity.class)` instead of the interface. `abstract class` not `interface`. Reads loot table via accessor mixin instead of `getLootTable()` method. |
| `mixin/MixinAbstractMinecartContainer.java` | **NEW** — replaces half of `MixinContainerEntity`. Cancels `unpackLootTable` on `AbstractMinecartContainer`. |
| `mixin/MixinChestBoat.java` | **NEW** — replaces the other half. Cancels `unpackLootTable` on `ChestBoat`. |
| `mixin/MixinContainerEntity.java` | **DELETED** — interface doesn't exist. |
| `mixin/AccessorRandomizableContainerBlockEntity.java` | **NEW** — `@Accessor("lootTable")` and `@Accessor("lootTableSeed")` for private fields. |
| `mixin/AccessorAbstractMinecartContainer.java` | **NEW** — same for the minecart container. |
| `mixin/AccessorChestBoat.java` | **NEW** — same for chest boat. |
| `handler/ContainerInteractionHandler.java` | `RandomizableContainerBlockEntity` (class) instead of `RandomizableContainer` (interface). Loot-table reads go through `((AccessorRandomizableContainerBlockEntity) (Object) rc).slashlootr$getLootTable()`. Loot table is `ResourceLocation`. |
| `handler/EntityInteractionHandler.java` | Branches on `AbstractMinecartContainer` vs `ChestBoat` (no shared `ContainerEntity` interface). Accessors for both. `ResourceLocation`. |
| `core/LootRoller.java` | Takes `ResourceLocation` (not `ResourceKey<LootTable>`). Loot table resolved via `level.getServer().getLootData().getLootTable(id)` instead of `reloadableRegistries()`. |
| `store/SlashLootrState.java` | Old 3-arg `computeIfAbsent(loader, factory, id)`. `save(CompoundTag)` without `HolderLookup.Provider`. `load(CompoundTag)` without it. |
| `store/PlayerLootEntry.java` | `SimpleContainer.fromTag(ListTag)` / `createTag()` without `HolderLookup.Provider`. |
| `resources/slashlootr.mixins.json` | Six mixins instead of two. `compatibilityLevel: JAVA_17`. |
| `build.gradle` | `it.options.release = 17` instead of 21. |
| `fabric.mod.json` | `"java": ">=17"` and `"fabricloader": ">=0.14.0"` (lower bound). |

**Total Band A delta**: 13 files changed, 4 net-new, 1 deleted, compared to the baseline.

**Build**: Loom 1.11.x, **Java 17**, Fabric API 0.92.2+1.20.1.

### Cross-band summary

| Band | MC | Diverging files | Mixins | Net-new files | API drift absorbed |
| ---- | -- | --------------- | ------ | ------------- | ------------------ |
| **A** | 1.20.1 | 10 | 6 | +4 | No `RandomizableContainer`/`ContainerEntity` interfaces; private loot-table fields → accessors; `ResourceLocation`; pre-`HolderLookup.Provider`; pre-`SavedData.Factory` |
| **B** | 1.20.5 | 0 | 2 | 0 | (Baseline-compatible; everything we use was introduced here) |
| **C** | 1.21, 1.21.1 | 0 | 2 | 0 | The baseline |
| **D** | 1.21.2, 1.21.4 | 2 | 2 | 0 | `ContainerEntity` loot-table accessor rename |
| **E** | 1.21.6, 1.21.9 | 2 | 2 | 0 | `SavedData.Factory` removed → `SavedDataType` + codec |
| **F** | 1.21.11 | 10 | 2 | 0 | Vehicle subpackage moves; `ResourceLocation`→`Identifier`; `.location()`→`.identifier()`; `Level#random` protected; `Commands.hasPermission` predicate; `SimpleContainer.addListener` removed → `DirtyContainer` subclass |
| **G** | 26.1.2 | 4 (vs F) | 2 | +1 (inlined `SeedDeriver`) | Quarantined Gradle 9.4 + Loom 1.15.5 + JDK 25; unobfuscated (no Mojang mappings); `SavedDataType` first arg now `Identifier` |

---

## 10. Comparison vs. Lootr / myLoot / LootrPlugin

| Aspect                              | Lootr                          | myLoot                         | LootrPlugin (Paper)        | **SlashLootr** |
| ----------------------------------- | ------------------------------ | ------------------------------ | -------------------------- | --------------- |
| Loader                              | Fabric, NeoForge, Forge        | Fabric                         | Paper plugin               | Fabric          |
| Required on clients                 | Yes                            | Yes                            | No                         | **No**          |
| Touches vanilla block-state         | Yes (`LootrChestBlock` swap)   | Yes (`MyLootChestBlock` swap)  | No                         | **No**          |
| Vanilla `/data` / selectors work    | No (it's a different block)    | No                             | Yes                        | **Yes**         |
| Per-player loot                     | ✓                              | ✓                              | ✓                          | ✓               |
| Block + entity containers           | ✓                              | ✓                              | ✓                          | ✓               |
| Looted-state visual indicator       | ✓ (custom block texture)       | ✓                              | ✗                          | ✗               |
| Loot decay / re-roll                | ✓                              | —                              | ?                          | ✗ (by design)   |
| Decorated pots / suspicious blocks  | ✓                              | ✗                              | ?                          | ✗               |
| MC versions covered                 | 1.12, 1.16–1.21.x              | 1.18–1.20.x                    | 1.18–26.1.x                | **1.20.1, 1.20.5, 1.21–1.21.9** |

The two relevant trade-offs SlashLootr accepts in exchange for vanilla compatibility:

1. **No looted-state visual.** Adding it would require a companion client mod, undermining the "vanilla clients" benefit.
2. **No lid animation.** We bypass `ContainerOpenersCounter` — solvable but not implemented in v1.

---

## 11. Limitations and rationale

### No chest lid animation

**Symptom**: players hear the open sound (via `OpenSoundFx`) but the lid doesn't move.

**Why**: vanilla lid animation is driven by `ContainerOpenersCounter` on the actual `ChestBlockEntity`. When `ChestMenu` is opened with a vanilla container, the menu calls `Container#startOpen(Player)` and `ChestBlockEntity#startOpen` increments the counter, which broadcasts a block event to clients to start the animation. Our personal `SimpleContainer` has a no-op `startOpen`, so nothing fires.

**Possible fix** (future): wrap the personal `SimpleContainer` in a small subclass that delegates `startOpen`/`stopOpen` to the underlying `ChestBlockEntity` (if still loaded). The animation would then play correctly. Care needed for the close path: we don't want to drop the menu reference before the per-player `SimpleContainer` is fully persisted.

### Hoppers don't pull from naturally-generated chests

**Symptom**: a hopper attached to a mineshaft chest extracts nothing.

**Why**: vanilla `HopperBlockEntity#tryGetContainer` calls `ContainerEntity#getItem(slot)` which calls `unpackLootTable` (which we cancel). The world container is permanently empty, so the hopper sees no items.

**Why we don't fix it**: matches Lootr's behavior. Per-player loot fundamentally conflicts with "hopper auto-pulls": which player's loot does the hopper extract? Documenting it is the right call. Player-placed chests with player-deposited items are unaffected (no `LootTable` tag → we never intercept).

### Comparator output is 0 from naturally-generated chests

Same root cause as the hopper issue. Lootr handles this with a mixin on `AbstractContainerMenu#getRedstoneSignalFromContainer` that returns a fixed value (or per-opener-state value). Solvable; we just haven't done it. Most worlds won't notice.

### Decorated pots and suspicious sand/gravel

These use a "brushing" open mechanic, not a right-click. Different vanilla hook (`BrushItem#useOn`, `BrushableBlockEntity#brush`). Lootr supports them via separate mixins. Out of v1 scope; would be a natural follow-up.

### No looted-state visual

Lootr changes the chest texture from gold to blue once a player has opened it. We can't do that server-side without sending the client a packet it doesn't understand. A companion client mod is the answer; deliberately deferred.

### Compile-clean only — no runtime verification

This is the biggest caveat. All 8 JARs compile, but none have been runtime-tested. The verification recipe in [`CLAUDE.md`](../CLAUDE.md#verification-manual-localserver) walks through the LocalServer test flow.

---

## 12. Future extensions

In rough order of effort vs. value:

1. **Runtime verification on LocalServer.** The biggest open item. Drop into `LocalServer/mods/` and walk through the two-player mineshaft test.
2. **Chest lid animation** via delegating `startOpen`/`stopOpen` to the underlying `ChestBlockEntity`.
3. **Comparator output** mixin for naturally-generated containers, returning a sensible per-player value.
4. **`/slashlootr forget all`** command — needs internal map exposure.
5. **Decorated pots / suspicious sand & gravel** — separate mixin hooks on the brushing path.
6. **Companion client mod** for the looted-state visual indicator. Optional install; server keeps working with vanilla clients.
7. **Loot decay / re-roll**: optional config knob to clear a player's personal copy after N MC days, prompting a re-roll on next open.
8. **Per-player seed strategy** as a config option (deterministic vs. true-random-per-open).

---

## 13. Adding a new band

Most of the time, a new MC version is a sub-day port. The hard part is identifying what changed.

### Step-by-step

1. **Find the right Fabric API version** for the target MC release. Cross-reference [`mindfulent/TipSign`](https://github.com/mindfulent/TipSign)'s `versions/` (it ships every MC version we care about) or [modrinth.com/mod/fabric-api/versions](https://modrinth.com/mod/fabric-api/versions).
2. **Pick the closest baseline band** by API generation: probably one of 1.20.5 / 1.21.1 / 1.21.4 / 1.21.6.
3. **Create the directory structure**:
   ```bash
   mkdir -p versions/<NEW>/src
   cp -r versions/<closest>/src/* versions/<NEW>/src/
   ```
4. **Write `versions/<NEW>/build.gradle`** and **`gradle.properties`** by copying the closest baseline's files and updating `minecraft_version` + `fabric_api_version`.
5. **Add to `settings.gradle`** and to the `dependsOn` + `bands` list in root `build.gradle`.
6. **Build it**:
   ```bash
   JAVA_HOME=/path/to/jdk-21 ./gradlew :versions:<NEW>:build
   ```
7. **Fix what the compiler flags.** Common drift to watch for:
   - **Method renames** (like 1.21.2's `getContainerLootTable`).
   - **Class removals** (like 1.21.6's `SavedData.Factory`).
   - **Method signature changes** (a new parameter type, or a parameter type renamed).
   - **Field visibility changes** that require new `@Accessor` mixins.
   - **Loom version requirement**: if the build fails with "Mod was built with a newer version of Loom", bump the version in root `build.gradle`.

### Cheap-path option (shared sources)

If the new band is binary-compatible with an existing one, you can skip the source copy and point `sourceSets` at the existing band's directory:

```groovy
sourceSets.main.java.srcDirs = [project(":versions:<existing>").file("src/main/java")]
sourceSets.main.resources.srcDirs = [project(":versions:<existing>").file("src/main/resources")]
```

This is what `versions/1.21/build.gradle` does — it points at 1.21.1's source tree. Only worth it for tight version pairs (1.21 / 1.21.1, 1.21.6 / 1.21.7 if it ever ships, etc.) where you're confident the API surface won't drift even slightly.

### Verifying a new band

`./gradlew buildAll` followed by inspecting `build/release/` for the new JAR. Then drop it into a LocalServer matching the target MC version and run through the [verification recipe in CLAUDE.md](../CLAUDE.md#verification-manual-localserver).

---

## See also

- [`README.md`](../README.md) — user-facing install and usage.
- [`CHANGELOG.md`](../CHANGELOG.md) — release history.
- [`CLAUDE.md`](../CLAUDE.md) — project guidance for future Claude sessions, including the LocalServer verification recipe.
- [Lootr source](https://github.com/LootrMinecraft/Lootr) — the prior art whose hook strategy informed this design.
- [myLoot source](https://github.com/spoorn/myLoot) — Fabric-specific reference for the loot-table-bearing container conversion approach.
