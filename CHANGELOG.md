# Changelog

All notable changes to SlashLootr. Dates are YYYY-MM-DD.

## 0.1.1 — 2026-05-25

Two new bands covering Mojang's late-2025 / early-2026 rename storm.

### Added

- **Band F: MC 1.21.11** — picks up the renames Mojang shipped at 1.21.11 that broke Band E on that version:
  - `net.minecraft.world.entity.vehicle.ChestBoat` → `…vehicle.boat.ChestBoat`
  - `net.minecraft.world.entity.vehicle.MinecartChest` → `…vehicle.minecart.MinecartChest`
  - `net.minecraft.world.entity.vehicle.MinecartHopper` → `…vehicle.minecart.MinecartHopper`
  - `net.minecraft.world.entity.vehicle.AbstractMinecartContainer` → `…vehicle.minecart.AbstractMinecartContainer`
  - `ResourceLocation` → `Identifier` (renamed in place at `net.minecraft.resources`)
  - `ResourceKey#location()` → `ResourceKey#identifier()`
  - `Level#random` (field) → `Level#getRandom()` (method)
  - `CommandSourceStack#hasPermission(int)` → `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` predicate
  - `SimpleContainer.addListener` removed → replaced with a `DirtyContainer extends SimpleContainer` that overrides `setChanged()` to mark the SavedData dirty
  - `SavedDataType` first arg still `String` (changes to `Identifier` in 26.1)
- **Band G: MC 26.1.2 ("Tiny Takeover")** — same rename surface as Band F plus:
  - **Quarantined**: own Gradle 9.4 wrapper, Loom 1.15.5, JDK 25 toolchain. Lives under `versions/26.1.2/` with its own gradlew. Main composite shells out via the `build26` task.
  - **Unobfuscated**: no `mappings loom.officialMojangMappings()` — Mojang names ship directly.
  - `SavedDataType` first arg now `Identifier` (via `Identifier.fromNamespaceAndPath`).
  - `SeedDeriver` inlined into the band's package since the main composite's `:common` subproject isn't reachable from the quarantine.

### Changed

- **Loom bumped 1.11 → 1.13.6** across the main composite. Verified backwards-compatible with Bands A through E. Required to add 1.21.11 as a regular subproject.
- **Band E (1.21.9) coverage narrowed** in publish scripts: was claimed to cover 1.21.9–1.21.11; now claims only 1.21.9 and 1.21.10 since 1.21.11 needs Band F's rename surface.
- **`build.gradle` root**: added `build26` `Exec` task and a quarantined-libs collect step in `buildAll` so the 10 JARs land together in `build/release/`.

### Runtime verified

- Band C (1.21.1): verified with two players on two machines.
- Bands A, B, D, E, F, G: compile-clean only.

---

## 0.1.0 — 2026-05-24

Initial implementation. Eight Minecraft version bands shipping out of the gate.

### Added

**Core mechanic** (all bands)
- Server-side per-player instanced loot for naturally-generated containers. Each player who opens a chest with a `LootTable` NBT tag receives a freshly-rolled personal inventory; one player's looting leaves the chest full for everyone else.
- Hook 1: mixin on the vanilla loot-roll method cancels the "lazy bake into the world container" pass — the chest's `LootTable` and `LootTableSeed` NBT tags stay intact across server restarts.
- Hook 2: Fabric `UseBlockCallback` + `UseEntityCallback` intercept right-click before vanilla menu-creation runs, build (or load) a per-player `SimpleContainer`, and open a vanilla `ChestMenu` / `ShulkerBoxMenu` / `HopperMenu` backed by it.
- Per-player loot seed: `containerSeed XOR player.uuid.msb XOR rotL(player.uuid.lsb, 17)` — same player + same container = same loot across re-opens.

**Container coverage**
- Chests, trapped chests, barrels, shulker boxes (block containers).
- Chest minecarts, hopper minecarts, chest boats (entity containers).
- Double chests: each half tracks its own per-player loot independently; opening either half presents a combined 54-slot `CompoundContainer` view, canonicalized by lower-packed `BlockPos`.

**Persistence**
- `world/<dimension>/data/slashlootr.dat`. Two top-level maps: `blocks` (keyed by packed `BlockPos`) and `entities` (keyed by entity UUID). Survives server restart.
- `ContainerListener` on every personal `SimpleContainer` marks the `SavedData` dirty on any slot change.

**Admin & config**
- `/slashlootr forget here|at <pos>|player <name>` (op-only).
- `config/slashlootr.json` with `dimensionBlocklist`, `lootTableBlocklist`, `playOpenCloseSounds`.

**Multi-version build infrastructure**
- Multi-project Gradle build patterned on `mindfulent/TipSign`. `common/` holds version-agnostic logic (just `SeedDeriver`); each band under `versions/<MC>/` is a self-contained subproject.
- `./gradlew buildAll` produces 8 JARs in `build/release/`.

### Per-band specifics

> See [`docs/ARCHITECTURE.md` § Per-band specifics](docs/ARCHITECTURE.md#per-band-specifics) for full file-level diffs.

| Band | MC versions | Loom | Java | Notable rewrites vs. 1.21.1 baseline |
| ---- | ----------- | ---- | ---- | ------------------------------------- |
| **A** | 1.20.1 | 1.11.x | 17 | `RandomizableContainer` interface didn't exist — mixin targets `RandomizableContainerBlockEntity` class. `ContainerEntity` interface didn't exist — split into separate `MixinAbstractMinecartContainer` + `MixinChestBoat`. Loot-table fields are private — added three `@Accessor` mixins (`AccessorRandomizableContainerBlockEntity`, `AccessorAbstractMinecartContainer`, `AccessorChestBoat`). `ResourceLocation` everywhere (no `ResourceKey<LootTable>`). `SavedData.computeIfAbsent(loader, factory, id)` 3-arg form, `save(CompoundTag)` without `HolderLookup.Provider`. `SimpleContainer.fromTag/createTag` without `HolderLookup.Provider`. |
| **B** | 1.20.5 | 1.11.x | 21 | Same source as Band C — compiles clean because 1.20.5 introduced `RandomizableContainer` interface, `ContainerEntity` interface, `ResourceKey<LootTable>`, and `HolderLookup.Provider` parameters. |
| **C** | 1.21, 1.21.1 | 1.11.x | 21 | The baseline. 1.21 shares 1.21.1's source tree via `sourceSets.main.java.srcDirs`. |
| **D** | 1.21.2, 1.21.4 | 1.11.x | 21 | `ContainerEntity.getLootTable()` → `getContainerLootTable()` (and the seed equivalent). Two-method rename in `EntityInteractionHandler` + `MixinContainerEntity`. |
| **E** | 1.21.6, 1.21.9 | 1.11.x | 21 | `SavedData.Factory<T>` removed; `SavedDataType<T>` + `Codec<T>` is the only registration path. Rewrote `SlashLootrState` with `RecordCodecBuilder` and a sparse `SlotItem(int slot, ItemStack item)` codec (encodes only non-empty slots). `PlayerLootEntry` lost its NBT methods in favor of a codec helper. |

### Build infrastructure

- Loom bumped from 1.9 → 1.10 → 1.11 as needed for newer MC versions.
- Fabric API versions per band cross-referenced against `mindfulent/TipSign` and Modrinth's Fabric API release list.

### Not yet implemented

- Chest lid animation (open sound plays via `OpenSoundFx`, but the lid stays closed because we bypass `ContainerOpenersCounter`).
- `/slashlootr forget all` — currently emits an error suggesting the admin stop the server and delete `slashlootr.dat`.
- Decorated pots and suspicious sand/gravel (different open mechanic — brushing — would need separate hooks).
- Looted-state visual indicator (Lootr's gold→blue color change would require a companion client mod).
- Loot decay / re-roll (each player's loot is currently permanent by design).
- Runtime verification on LocalServer or a production deploy — code is compile-clean only.
