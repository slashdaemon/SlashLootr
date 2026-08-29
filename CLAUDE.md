# CLAUDE.md — SlashLootr

Server-side per-player loot mod, published as **SlashLoot**. **No custom blocks.** **No client install required.** Vanilla-compatible alternative to [Lootr](https://github.com/LootrMinecraft/Lootr).

## Why this exists

Lootr/myLoot achieve per-player chests by swapping vanilla blocks for custom `LootrChestBlock`/`MyLootChestBlock` variants. That breaks vanilla compatibility and requires clients to install the mod. SlashLoot does the same job by intercepting two vanilla code paths server-side — the chest stays a `minecraft:chest` and vanilla clients connect normally.

## How it works

1. **Mixin** `RandomizableContainer#unpackLootTable` (and `ContainerEntity#unpackChestVehicleLootTable`) → cancel the vanilla loot roll. The chest's `LootTable`/`LootTableSeed` NBT tags persist forever; the world container stays "unrolled."
2. **Loader event on player right-click** → look up or roll a personal container from a per-dimension `SavedData`, then `openMenu(...)` a vanilla `ChestMenu` backed by it.

Per-player seed: `XOR(containerSeed, player.uuid.msb, rotL(player.uuid.lsb, 17))` — deterministic, so re-opening shows what you left.

Persistence: `world/<dim>/data/slashlootr.dat`. Two maps inside: `blocks` (keyed by packed `BlockPos`) and `entities` (keyed by entity UUID).

### `core/Handling` is the single decision point

**Both the mixins and the interaction handlers ask `Handling` and nothing else** whether a container
is ours. That is not a style choice — it is the fix for the two worst bugs in 0.1.x, where the mixin
cancelled the vanilla roll for containers the handlers then refused to serve, leaving them
permanently empty. A `VANILLA` verdict means the mixin does not cancel and the container behaves as
if SlashLoot were absent.

**If you add a new container type or a new skip condition, it goes in `Handling`.** Never add a
condition to a handler or a mixin alone.

`Handling` runs from `unpackLootTable`, which hopper polling reaches repeatedly, so it must stay
side-effect free and must never force-load a chunk.

## Build

Java 21 for bands A–F (Java 17 target for Band A); Band G (26.1.x) needs JDK 25 via its own wrapper.

```bash
export JAVA_HOME="/c/Users/slash/AppData/Roaming/PrismLauncher/java/java-runtime-delta"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew buildAll                      # all bands → build/release/
./gradlew :versions:1.21.1-fabric:build # single band
./gradlew build26                       # quarantined Band G only
```

Verify Prism's JDK still aliases as `java-runtime-delta` (Prism rotates these — check `ls ~/AppData/Roaming/PrismLauncher/java/` if Gradle complains about the toolchain).

## Layout

One shared source tree, composed per band. **There are no per-band copies of the mod logic** —
a fix is written once.

```
SlashLootr/
├── common/                     SeedDeriver — plain Java, no MC types
├── mc-src/                     ALL shared mod logic, ONE copy (bands B–G)
│   └── src/main/java/dev/blockacademy/slashlootr/
│       ├── SlashLootrCore.java         boot(LoaderBridge)
│       ├── loader/LoaderBridge.java    the ONLY Fabric/NeoForge seam
│       ├── core/Handling.java          THE decision function (read this first)
│       ├── core/LootContainerBase.java dirty tracking + open/close delegation
│       ├── core/{ContainerKind,LootRoller,OpenSoundFx,DebugLog}.java
│       ├── handler/{ContainerInteraction,EntityInteraction,Cleanup}Handler.java
│       ├── command/SlashLootCommand.java
│       ├── config/SlashLootrConfig.java
│       ├── store/PlayerLootEntry.java
│       └── mixin/{MixinRandomizableContainer,MixinContainerEntity,MixinEntityRemoved}.java
├── compat/                     per-generation seams, a few dozen lines each
│   ├── ids-location/           B–E   ResourceKey#location(), hasPermission(2)
│   ├── ids-identifier/         F–G   ResourceKey#identifier(), Commands.hasPermission
│   ├── vehicle-legacy/         B–C   ContainerEntity#getLootTable
│   ├── vehicle-container/      D–E   #getContainerLootTable
│   ├── vehicle-moved/          F–G   + vehicle.minecart / vehicle.boat packages
│   ├── store-nbt/              B–D   SavedData.Factory + CompoundTag
│   ├── store-codec/            E–G   SavedDataType + Codec
│   ├── savedtype-string/       E–F   SavedDataType(String, …)
│   ├── savedtype-id/           G     SavedDataType(Identifier, …)
│   ├── open-player/            B–E   Container#startOpen(Player)
│   └── open-containeruser/     F–G   Container#startOpen(ContainerUser)
├── loader-fabric/              FabricBridge + entrypoint + fabric.mod.json + mixins.json
├── gradle/fabric-band.gradle   composes the above from each band's variant list
└── versions/
    ├── 1.20.1-fabric/          Band A — SELF-CONTAINED FORK (see below)
    ├── <band>-fabric/          build.gradle + gradle.properties only
    └── 26.1.2/                 Band G — quarantined composite (own wrapper, JDK 25)
```

A band's whole build file is its variant list:

```gradle
plugins { id "fabric-loom" }
ext.slashloot = [ids: "location", vehicle: "legacy", store: "nbt", open: "player"]
apply from: "${rootDir}/gradle/fabric-band.gradle"
```

### Band A is deliberately a fork

`versions/1.20.1-fabric/` keeps its own full copy of the sources. MC 1.20.1 predates the
`RandomizableContainer` and `ContainerEntity` interfaces, stores loot tables as `ResourceLocation`
rather than `ResourceKey<LootTable>`, and keeps those fields private (hence the `@Accessor` mixins).
Sharing it would mean an opaque loot-reference abstraction across every band to serve one legacy
Fabric-only version. **Changes to `mc-src` must be ported to Band A by hand** — its `Handling` keeps
the same contract and the same reason strings, so the port is mechanical.

## Currently shipping

10 JARs, all via `./gradlew buildAll`. Artifacts are `slashlootr-<ver>+mc<band>-<loader>.jar`.

| Band | MC | ids | vehicle | store | savedtype | open |
| ---- | -- | --- | ------- | ----- | --------- | ---- |
| A | 1.20.1 | *(fork — Java 17)* | | | | |
| B | 1.20.5–1.20.6 | location | legacy | nbt | — | player |
| C | 1.21, 1.21.1 | location | legacy | nbt | — | player |
| D | 1.21.2, 1.21.4 | location | container | nbt | — | player |
| E | 1.21.6 | location | container | codec | string | player |
| E | 1.21.9 | location | container | codec | string | containeruser |
| F | 1.21.11 | identifier | moved | codec | string | containeruser |
| G | 26.1.2 | identifier | moved | codec | id | containeruser |

Where each split lands: `getContainerLootTable` at **1.21.2**; `SavedDataType`+`Codec` at **1.21.6**;
`startOpen(ContainerUser)` at **1.21.9**; `Identifier` + vehicle package move at **1.21.11**;
`SavedDataType(Identifier)` at **26.1**.

## Adding a new band

1. `include "versions:<MC>-fabric"` in `settings.gradle` and add it to `fabricBands` in the root `build.gradle`.
2. Create `versions/<MC>-fabric/gradle.properties` (`minecraft_version`, `fabric_api_version`; add `java_version` / `mixin_compat` only if it is not JDK 21).
3. Create `versions/<MC>-fabric/build.gradle` with the four-key variant list above — pick the row from the table whose splits the new version is on.
4. Build. **If it compiles, you are done.** If it does not, the compiler is telling you a new drift axis appeared: add a `compat/<axis>-<variant>/` directory holding only the differing calls, add the key to `gradle/fabric-band.gradle`, and set it on every band. Do not fork the whole tree.

## Verification

**Build gate:** `./gradlew buildAll` must collect the expected JAR count into `build/release/`.

**Headless functional pass** (no client needed — a hopper under a container triggers
`unpackLootTable`, which is the exact path the mixins hook). Boot a bare Fabric server with the
band's JAR + Fabric API, then over RCON:

```
setblock <p> minecraft:chest{LootTable:"minecraft:chests/simple_dungeon",LootTableSeed:1L}
setblock <p below> minecraft:hopper[facing=down]
data get block <p>          # instanced: LootTable tag SURVIVES, hopper stays empty
                            # blacklisted/unsupported: tag GONE, hopper holds real vanilla loot
```

Cover: chest (instanced), chest with its table in `lootTableBlocklist` (must fall back to vanilla),
`minecraft:hopper` with a loot table (unsupported container — must fall back to vanilla), barrel,
chest minecart. Set `debugLogging: true` and check each verdict line reads correctly.

**Client pass** (needs two players; LocalServer at `C:\Users\slash\Projects\LocalServer\`):

1. `/locate structure minecraft:mineshaft` → dig to a chest → `/data get block ~ ~ ~ LootTable` shows a table id
2. Player A opens it → notes items; Player B opens → **different items**; A re-opens → same as before
3. Restart the server → both players' loot persists; the `LootTable` tag is still on the block
4. Lid animates on open, and the open sound plays **once**
5. Redstone lamp beside a natural **trapped** chest powers while open
6. Break the chest → `/slashloot stats` block count drops by one
7. Place a player-crafted chest → no interception

Repeat for: trapped chest, barrel, shulker box, chest minecart, double chest (both lids), chest boat.

## Known limitations

- **Hoppers extract nothing** from naturally-generated containers SlashLoot instances (the world container is always empty). Matches Lootr. Blacklisted and unsupported containers are unaffected.
- **Comparator output reads 0** from those same containers, for the same reason.
- **No decay / re-roll** — per-player loot is permanent by design.
- **Prune skips unloaded chunks.** Entries for containers destroyed in chunks that never load again are not reclaimed; they are cheap and get dropped on the next pass that finds the chunk loaded.

## Repository

`slashdaemon/SlashLootr`. No `Co-Authored-By: Claude` trailer on commits. Not part of the TBA modpack — it is a server-side-only addon.
