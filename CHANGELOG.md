# Changelog

All notable changes to SlashLootr. Dates are YYYY-MM-DD.

## 0.2.0 — 2026-08-29

Compatibility release, driven by modpack-author feedback on the CurseForge page.

### Fixed

- **Blacklisted dimensions and loot tables served empty containers.** The loot-cancelling mixins
  cancelled the vanilla roll unconditionally while the interaction handlers skipped anything
  blacklisted, so a blacklisted container was never rolled by anyone. Both sides now ask the same
  question through a single decision function (`core/Handling`), and a "vanilla" verdict means the
  container behaves exactly as if SlashLoot were not installed — including hopper extraction and
  comparator output.
- **Unknown modded containers were broken the same way.** `unpackLootTable` is a default method on
  the `RandomizableContainer` interface, so the mixin fired for every implementor in the game,
  including containers SlashLoot has no idea how to serve. Anything not actually instanced is now
  left completely alone. Opt in with `handleUnknownContainers`.
- **Container slot count is read from the world container** instead of assuming 27, so a modded
  chest with a different inventory size is no longer silently truncated.
- **`/slashloot forget all` works.** It previously printed an instruction to stop the server and
  delete the `.dat` file by hand.

### Added

- **Chest lids animate again.** Personal containers delegate `startOpen`/`stopOpen` to the real
  world container, so vanilla's `ContainerOpenersCounter` runs: chest and shulker lids animate,
  barrels flip their `open` blockstate, and **trapped chests emit redstone**. Both halves of a
  double chest animate. Controlled by `delegateContainerAnimation` (default on); with it on, the
  manual open sound is suppressed so the sound does not play twice.
- **Stored loot is cleaned up when a container is destroyed**, in three layers: the player-break
  event, a loader-neutral mixin on entity removal (filtered on `RemovalReason#shouldDestroy` so a
  chunk unload never wipes a chest minecart), and a background prune that re-checks stored
  positions in loaded chunks to catch explosions, pistons, and world edits.
  Config: `cleanupOnBreak`, `pruneIntervalTicks`, `pruneBatchSize`.
- **Optional decision logging** (`debugLogging`). One line per container giving position, loot
  table, verdict, and reason (`dimension_blocklisted`, `unsupported_container:<id>`, …). Emitted
  from the decision function itself, so it always reflects what actually happened. Repeat verdicts
  for the same container are deduplicated so hopper polling cannot flood the log.
- **New commands:** `/slashloot prune`, `/slashloot stats`, `/slashloot reload`.
- **New config keys:** `enabled`, `handleUnknownContainers`, `delegateContainerAnimation`,
  `cleanupOnBreak`, `pruneIntervalTicks`, `pruneBatchSize`, `debugLogging`. Missing keys take
  their defaults, so a 0.1.x config keeps working.

### Changed

- **JARs are now named `slashlootr-<version>+mc<band>-fabric.jar`.** The loader suffix makes room
  for the NeoForge builds landing in 0.3.0.
- **Band G (26.1.2) mixin config now declares `JAVA_25`**, matching the JDK it is compiled with.
  It was shipping `JAVA_21`.

### Internal

- **The nine per-band source forks are gone.** All bands from 1.20.5 up compile one shared tree
  (`mc-src/`) plus small per-generation compat variants (`compat/ids-*`, `vehicle-*`, `store-*`,
  `savedtype-*`, `open-*`) and a loader adapter (`loader-fabric/`). A fix is written once instead
  of nine times. Band A (1.20.1) remains a self-contained fork — it predates the
  `RandomizableContainer` interface and `ResourceKey<LootTable>` — and carries the same fixes.
- Mod id, `config/slashlootr.json`, and `world/<dim>/data/slashlootr.dat` are unchanged, so
  existing server data carries over untouched.

### Runtime verified

- Band C (1.21.1): blacklist fallback, unknown-container fallback, and decision logging verified
  end-to-end on a headless server for chests, barrels, hoppers-with-loot-tables, and chest
  minecarts. Commands and config reload exercised over RCON. No mixin warnings on boot.
- Bands A, B, D, E, F, G: compile-clean.

## 0.1.2 — 2026-05-31

Branding pass: the project is now published as **SlashLoot** to keep the listing distinct and unambiguous.

### Changed

- **Admin command renamed `/slashlootr` → `/slashloot`** across all bands, to match the new project name.
- Command feedback messages now prefixed `SlashLoot:` instead of `SlashLootr:`.
- **License is now CC-BY-4.0** (was MIT) — `LICENSE`, every band's `fabric.mod.json`, and the listing descriptions updated to match.
- Listing descriptions (Modrinth/CurseForge) and README rewritten to remove third-party-mod comparisons.

### Unchanged (intentionally)

- Mod id, package, config file (`config/slashlootr.json`), and the per-dimension save file (`world/<dim>/data/slashlootr.dat`) keep the `slashlootr` identifier so existing server data is preserved across the update.

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
- Multi-project Gradle build patterned on `slashdaemon/TipSign`. `common/` holds version-agnostic logic (just `SeedDeriver`); each band under `versions/<MC>/` is a self-contained subproject.
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
- Fabric API versions per band cross-referenced against `slashdaemon/TipSign` and Modrinth's Fabric API release list.

### Not yet implemented

- Chest lid animation (open sound plays via `OpenSoundFx`, but the lid stays closed because we bypass `ContainerOpenersCounter`).
- `/slashlootr forget all` — currently emits an error suggesting the admin stop the server and delete `slashlootr.dat`.
- Decorated pots and suspicious sand/gravel (different open mechanic — brushing — would need separate hooks).
- Looted-state visual indicator (Lootr's gold→blue color change would require a companion client mod).
- Loot decay / re-roll (each player's loot is currently permanent by design).
- Runtime verification on LocalServer or a production deploy — code is compile-clean only.
