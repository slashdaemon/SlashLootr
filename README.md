# SlashLootr

Server-side per-player loot for naturally-generated containers in Minecraft Fabric. **No client install required. No custom blocks.** A vanilla-compatible alternative to [Lootr](https://modrinth.com/mod/lootr).

## What it does

Each player who opens a naturally-generated chest, barrel, shulker box, chest minecart, or chest boat sees their own personal copy of the loot rolled from that container's loot table. One player's looting doesn't drain the chest for anyone else. No racing to dungeons; no empty mineshafts after the speedrunner went through.

Player-placed containers behave exactly as in vanilla.

## Why a new mod when Lootr exists?

Lootr (and myLoot) implement per-player loot by **registering custom block variants** and swapping the vanilla block-state at the container's position. This:

- requires the mod on **every client** (mismatched blockstates kick vanilla clients)
- breaks scoreboard selectors, datapack predicates, structure saves, and other mods that scan for `minecraft:chest`
- the Lootr authors have [publicly invited](https://modrinth.com/mod/lootr) plugin developers to write a server-only Fabric alternative

SlashLootr never touches block-state. The chest stays a `minecraft:chest` forever. Every player gets their own copy entirely via server-side menu substitution.

| Feature                                  | Lootr | myLoot | SlashLootr |
| ---------------------------------------- | :---: | :----: | :--------: |
| Per-player loot for naturally-gen chests |  ✓    |   ✓    |     ✓      |
| Works on dedicated server                |  ✓    |   ✓    |     ✓      |
| Vanilla clients (no client install)      |  ✗    |   ✗    |     ✓      |
| Block stays `minecraft:chest`            |  ✗    |   ✗    |     ✓      |
| Per-player loot for chest minecarts/boats|  ✓    |   ✓    |     ✓      |
| Looted-state visual (gold→blue)          |  ✓    |   ✓    |     ✗      |
| Loot decay / re-roll                     |  ✓    |   —    |     ✗      |

Full technical comparison and architecture deep-dive: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Supported versions

8 bands, all built via `./gradlew buildAll`. Pick the JAR that matches your server's Minecraft version.

| Minecraft | Fabric API     | JAR                                |
| --------- | -------------- | ---------------------------------- |
| 1.20.1    | 0.92.2+1.20.1  | `slashlootr-0.1.0+mc1.20.1.jar`    |
| 1.20.5    | 0.97.8+1.20.5  | `slashlootr-0.1.0+mc1.20.5.jar`    |
| 1.21      | 0.102.0+1.21   | `slashlootr-0.1.0+mc1.21.jar`      |
| 1.21.1    | 0.115.6+1.21.1 | `slashlootr-0.1.0+mc1.21.1.jar`    |
| 1.21.2    | 0.106.1+1.21.2 | `slashlootr-0.1.0+mc1.21.2.jar`    |
| 1.21.4    | 0.114.0+1.21.4 | `slashlootr-0.1.0+mc1.21.4.jar`    |
| 1.21.6    | 0.128.1+1.21.6 | `slashlootr-0.1.0+mc1.21.6.jar`    |
| 1.21.9    | 0.134.1+1.21.9 | `slashlootr-0.1.0+mc1.21.9.jar`    |

Each release also ships an exact-version Fabric API requirement — match it.

For the breakdown of which vanilla APIs each band needs and what was rewritten per band, see [`docs/ARCHITECTURE.md` § Per-band specifics](docs/ARCHITECTURE.md#per-band-specifics).

## Installation

1. Drop the matching JAR into your server's `mods/` folder.
2. Drop Fabric API for that Minecraft version into the same folder.
3. Restart the server.

That's it. Clients connect with vanilla Fabric (no SlashLootr in their mods folder).

## How it works (one paragraph)

Two intercepts on the vanilla loot path:

1. **Mixin on `RandomizableContainer#unpackLootTable`** (`RandomizableContainerBlockEntity` on Band A; `ContainerEntity#unpackChestVehicleLootTable` for entity containers on Bands B–E): cancel the vanilla "lazy bake loot into the world container" pass. The chest's `LootTable` and `LootTableSeed` NBT tags stay intact forever — from the world's perspective, the chest is permanently "not yet rolled."
2. **Fabric `UseBlockCallback` and `UseEntityCallback`**: when a player right-clicks a container that still has a `LootTable` tag, look up (or roll fresh) a per-player `SimpleContainer` from a per-dimension `SavedData`, and `player.openMenu(...)` a vanilla `ChestMenu` / `ShulkerBoxMenu` / `HopperMenu` backed by that personal container. Vanilla client sees a normal chest UI synced against personal items.

Per-player seed: `containerSeed XOR player.uuid.msb XOR rotL(player.uuid.lsb, 17)` — same player on same chest gives stable loot.

Persistence lives at `world/<dimension>/data/slashlootr.dat`. Full sequence diagram, identity scheme, and per-band file map: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Configuration

`config/slashlootr.json` (created on first launch):

```json
{
  "dimensionBlocklist": [],
  "lootTableBlocklist": [],
  "playOpenCloseSounds": true
}
```

- `dimensionBlocklist`: dimensions (e.g. `"minecraft:the_nether"`) where SlashLootr is disabled and vanilla loot behavior applies.
- `lootTableBlocklist`: loot tables (e.g. `"minecraft:chests/buried_treasure"`) to skip — useful for adventure maps that intentionally use shared loot.
- `playOpenCloseSounds`: whether to play the chest open sound on personal-menu open (set false if you find it noisy).

## Admin commands

All require permission level 2 (op).

| Command                                  | Effect                                                                  |
| ---------------------------------------- | ----------------------------------------------------------------------- |
| `/slashlootr forget here`                | Wipe every player's personal loot at the block you're looking at        |
| `/slashlootr forget at <x> <y> <z>`      | Same, by coordinate                                                     |
| `/slashlootr forget player <player>`     | Wipe a player's personal loot at every container in the current dim     |
| `/slashlootr forget all`                 | (Not yet implemented; stop server and delete `data/slashlootr.dat`)     |

## Known limitations

- **No chest lid animation.** Players hear the open sound but see no lid movement — the per-player menu bypasses vanilla's `ContainerOpenersCounter`. Future work could delegate `startOpen`/`stopOpen` to the underlying chest BE; not blocking for v1.
- **Hoppers don't pull from naturally-generated chests.** Since vanilla never bakes loot into the chest, hoppers attached to one see an empty container and extract nothing. Matches Lootr's behavior. Player-placed chests are unaffected.
- **Comparator output is always 0** for naturally-generated chests for the same reason. Most map-makers won't care; flag for redstone-heavy worlds.
- **Decorated pots and suspicious sand/gravel** are not covered. They use a different open mechanic (brushing) — would need separate hooks.
- **No looted-state visual indicator.** Lootr's gold→blue color change would require a client mod. Out of scope for v1.

Rationale for each limitation lives in [`docs/ARCHITECTURE.md` § Limitations and rationale](docs/ARCHITECTURE.md#limitations-and-rationale).

## Building from source

Requires JDK 21 (and JDK 17 if you want to build the 1.20.1 band — Loom will fetch a toolchain automatically if needed). The build scripts target Prism Launcher's bundled JDK by default; adjust `JAVA_HOME` if you have your own.

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew buildAll
# 8 JARs land in build/release/
```

Single band:

```bash
./gradlew :versions:1.21.1:build
# JAR in versions/1.21.1/build/libs/
```

To add a new band, see [`docs/ARCHITECTURE.md` § Adding a new band](docs/ARCHITECTURE.md#adding-a-new-band).

## License

MIT. See `LICENSE`.

## Acknowledgements

- [Lootr](https://github.com/LootrMinecraft/Lootr) by noobanidus — the original. SlashLootr's hook strategy is informed by reading their `MixinRandomizableContainer`.
- [myLoot](https://github.com/spoorn/myLoot) by spoorn — Fabric-specific reference for the loot-table-bearing container conversion approach.
- [LootrPlugin](https://modrinth.com/plugin/lootrplugin) — proves the server-only approach works on Paper.
