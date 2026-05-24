# CLAUDE.md — SlashLootr

Server-side per-player loot mod for Fabric. **No custom blocks.** **No client install required.** Vanilla-compatible alternative to [Lootr](https://github.com/LootrMinecraft/Lootr).

## Why this exists

Lootr/myLoot achieve per-player chests by swapping vanilla blocks for custom `LootrChestBlock`/`MyLootChestBlock` variants. That breaks vanilla compatibility and requires clients to install the mod. SlashLootr does the same job by intercepting two vanilla code paths server-side — the chest stays a `minecraft:chest` and vanilla clients connect normally.

## How it works

1. **Mixin** `RandomizableContainer#unpackLootTable` (and `ContainerEntity#unpackChestVehicleLootTable`) → cancel the vanilla loot roll. The chest's `LootTable`/`LootTableSeed` NBT tags persist forever; the world container stays "unrolled."
2. **Fabric `UseBlockCallback`/`UseEntityCallback`** → on player right-click, look up or roll a personal `SimpleContainer` from a per-dimension `SavedData`, then `player.openMenu(...)` a vanilla `ChestMenu` backed by that personal container.

Per-player seed: `XOR(containerSeed, player.uuid.msb, rotL(player.uuid.lsb, 17))` — deterministic, so re-opening shows what you left.

Persistence: `world/<dim>/data/slashlootr.dat`. Two maps inside: `blocks` (keyed by packed `BlockPos`) and `entities` (keyed by entity UUID).

## Build

Java 21 required for all currently-shipped bands.

```bash
export JAVA_HOME="/c/Users/slash/AppData/Roaming/PrismLauncher/java/java-runtime-delta"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew buildAll            # all bands → build/release/
./gradlew :versions:1.21.1:build   # single band
```

Verify Prism's JDK still aliases as `java-runtime-delta` (Prism rotates these — check `ls ~/AppData/Roaming/PrismLauncher/java/` if Gradle complains about the toolchain).

## Layout

```
SlashLootr/
├── common/                  # pure Java 17, version-agnostic
│   └── src/main/java/dev/blockacademy/slashlootr/common/SeedDeriver.java
├── versions/
│   ├── 1.21.1/              # primary band (TBA target), full source lives here
│   │   ├── build.gradle
│   │   ├── gradle.properties
│   │   └── src/main/
│   │       ├── java/dev/blockacademy/slashlootr/v1_21_1/
│   │       │   ├── SlashLootrMod.java
│   │       │   ├── mixin/MixinRandomizableContainer.java
│   │       │   ├── mixin/MixinContainerEntity.java
│   │       │   ├── handler/ContainerInteractionHandler.java     # UseBlockCallback
│   │       │   ├── handler/EntityInteractionHandler.java        # UseEntityCallback
│   │       │   ├── store/SlashLootrState.java                   # PersistentState/SavedData
│   │       │   ├── store/PlayerLootEntry.java
│   │       │   ├── core/LootRoller.java                         # LootTable#fill wrapper
│   │       │   ├── core/ContainerKind.java                      # menu type + slot count
│   │       │   ├── core/OpenSoundFx.java                        # plays open sound
│   │       │   ├── command/SlashLootrCommand.java               # /slashlootr forget ...
│   │       │   └── config/SlashLootrConfig.java                 # config/slashlootr.json
│   │       └── resources/
│   │           ├── fabric.mod.json
│   │           └── slashlootr.mixins.json
│   └── 1.21/                # symlink-via-Gradle to 1.21.1's sources (compatible API)
```

## Currently shipping

8 bands, all built via `./gradlew buildAll`:

| Band | MC | Notes |
| ---- | -- | ----- |
| A | 1.20.1 | Java 17. `RandomizableContainerBlockEntity` class target; no `ContainerEntity` interface — separate mixins on `AbstractMinecartContainer`/`ChestBoat`. `ResourceLocation` (not `ResourceKey<LootTable>`). Private loot-table fields accessed via `@Accessor` mixins. `SavedData.computeIfAbsent` 3-arg form, `save(CompoundTag)` without `HolderLookup.Provider`. |
| B | 1.20.5 | `RandomizableContainer`/`ContainerEntity` interfaces. `ResourceKey<LootTable>`. `HolderLookup.Provider` added. |
| C | 1.21, 1.21.1 | Same surface as 1.20.5. 1.21 shares sources with 1.21.1 via `sourceSets.main.java.srcDirs`. |
| D | 1.21.2, 1.21.4 | `ContainerEntity.getLootTable()` → `getContainerLootTable()`. Otherwise same as Band C. |
| E | 1.21.6, 1.21.9 | `SavedData.Factory` removed; `SavedDataType<T>` + `Codec<T>` only. `SlashLootrState` rewritten with `RecordCodecBuilder`. Needs Loom 1.10+ (1.21.6) / 1.11+ (1.21.9). |

## Adding a new band

1. **Cheap path**: if the target shares a baseline band's API surface, mirror that band's `build.gradle`. For 1.21+ this means just `dependencies`/`processResources` + `sourceSets.main.java.srcDirs = [project(":versions:<baseline>").file("src/main/java")]` if compatible. Update `gradle.properties` with the right `minecraft_version`/`fabric_api_version` (cross-reference TipSign's `versions/` for known-good fabric-api releases per MC version, or modrinth.com/mod/fabric-api/versions).
2. **If it doesn't compile**: copy the closest-baseline band's full `src/main/` tree to `versions/<NEW>/src/main/` and adapt only what the compiler flags. Known drift to watch for is summarized in the table above.
3. Add the band to `settings.gradle` and to `buildAll`'s `dependsOn` + `bands` list in root `build.gradle`.
4. Loom version: 1.10+ for 1.21.6+, 1.11+ for 1.21.9+. Bump in root `build.gradle` if needed.

## Verification (manual, LocalServer)

LocalServer (`C:\Users\slash\Projects\LocalServer\`) is the test rig. Mirrors prod deploy via mrpack4server.

```bash
# 1. Build
./gradlew :versions:1.21.1:build

# 2. Deploy to LocalServer
cp versions/1.21.1/build/libs/slashlootr-0.1.0+mc1.21.1.jar /c/Users/slash/Projects/LocalServer/mods/

# 3. Start in fresh mode
cd /c/Users/slash/Projects/LocalServer && python server-config.py
# pick "fresh" mode → "start"
```

End-to-end test (two players required):

1. `/locate structure minecraft:mineshaft` → tp to result → dig to find a chest
2. Verify it's naturally-generated: `/data get block ~ ~ ~ LootTable` → should show a loot table ID
3. Player A opens it → notes the items
4. Player B opens it → should be **different items**
5. Player A closes, re-opens → same items as step 3
6. `/stop` then restart → both players' loot persists across the boot
7. `/data get block ~ ~ ~ LootTable` on the same chest → **the loot table tag is still there** (proves we never let vanilla bake)
8. Place a player-crafted chest, drop items in, re-open → still shows your dropped items (no interception for player-placed)

Repeat for: trapped chest (jungle temple), barrel (village), shulker box (ancient city), chest minecart (mineshaft cart on rails), double chest (stronghold library), chest boat (buried treasure ocean ruins).

## Known limitations (v1)

- **No chest lid animation.** Open sound plays via `OpenSoundFx`, but lid stays closed (we bypass `ContainerOpenersCounter`). Would need either delegating `startOpen`/`stopOpen` to the underlying chest BE or sending block events manually.
- **Hoppers extract nothing** from naturally-generated chests (world container is always empty). Matches Lootr behavior. Document for players.
- **Comparator output reads 0** from naturally-generated chests for the same reason.
- **No decay / re-roll** — per-player loot is permanent. User chose this in plan.

## Repository

Not yet pushed. Per project CLAUDE.md collaboration rules: no `Co-Authored-By: Claude` trailer on commits. Don't deploy to TBA without explicit approval (mod isn't even there yet — it doesn't go in the modpack, it goes in `server-config.py update-pack` as a server-side-only addon).
