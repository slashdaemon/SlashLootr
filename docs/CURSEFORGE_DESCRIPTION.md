# SlashLoot

**Per-player loot for naturally-generated chests, barrels, shulker boxes, and minecarts — server-side only, no client install required.**

Every player who opens a naturally-generated container gets their own personal copy of the loot. Open a mineshaft chest and get your roll; your friend opens that same chest and gets a separate roll of their own. No more racing to dungeons, and nobody arrives to find an empty chest after someone else already cleared it out.

> **v0.1.1 — running in production** on The Block Survival, our Minecraft 26.1.2 vanilla-friendly survival server. Deployed entirely server-side; players didn't have to update or install anything.

**Fabric and NeoForge.**

## Server-side only — players install nothing

SlashLoot runs entirely on the server. Players connect with plain vanilla Fabric: they don't install the mod, and they don't have to update when you do. SlashLoot never replaces the block or its block-state, so the container always stays a real `minecraft:chest` (or barrel, shulker, minecart, etc.). Datapack predicates, `/data` queries, scoreboard selectors, and structure saves keep seeing ordinary vanilla blocks.

## Features

- **Per-player instanced loot** for naturally-generated containers. One open = one personal roll.
- **Persists across server restart.** Each player's personal loot is saved per-dimension.
- **Same loot if you re-open.** Close your menu with items still inside? They're still there next time.
- **Player-placed containers are unaffected** — vanilla behavior preserved for chests you built.
- **Double-chest support.** Each half tracks per-player loot independently; opening either half shows a combined 54-slot view.
- **Configurable.** Per-dimension and per-loot-table blocklists for adventure-map compatibility.
- **Admin commands.** `/slashloot forget here|at|player` to wipe personal loot when needed.

## Container coverage

- Chests, trapped chests, barrels, shulker boxes
- Chest minecarts, hopper minecarts, chest boats
- Double chests (54-slot combined view)

## Installation

1. Drop the JAR matching your Minecraft version into your server's `mods/` folder.
2. Drop the matching Fabric API version into the same folder.
3. Restart the server.

That's it. Clients connect with vanilla Fabric — nothing to install on their end.

## Supported versions

10 separate JARs — pick the one that matches your server's Minecraft version.

| Minecraft | Fabric API |
|---|---|
| 1.20.1 | 0.92.2+1.20.1 |
| 1.20.5 | 0.97.8+1.20.5 |
| 1.21 | 0.102.0+1.21 |
| 1.21.1 | 0.115.6+1.21.1 |
| 1.21.2 | 0.106.1+1.21.2 |
| 1.21.4 | 0.114.0+1.21.4 |
| 1.21.6 | 0.128.1+1.21.6 |
| 1.21.9 | 0.134.1+1.21.9 |
| 1.21.11 | 0.141.2+1.21.11 |
| 26.1.2 | 0.146.1+26.1.2 |

## Configuration

A `config/slashlootr.json` file is created on first launch:

```json
{
  "enabled": true,
  "dimensionBlocklist": [],
  "lootTableBlocklist": [],
  "handleUnknownContainers": false,
  "delegateContainerAnimation": true,
  "playOpenCloseSounds": true,
  "cleanupOnBreak": true,
  "pruneIntervalTicks": 6000,
  "pruneBatchSize": 256,
  "debugLogging": false
}
```

- **`enabled`** — master switch. `false` makes SlashLoot inert; every container behaves like vanilla.
- **`dimensionBlocklist`** — dimensions (e.g. `"minecraft:the_nether"`) served as ordinary shared vanilla loot.
- **`lootTableBlocklist`** — loot tables (e.g. `"minecraft:chests/buried_treasure"`) to skip. Useful for adventure maps with intentionally shared loot.
- **`handleUnknownContainers`** — instance modded containers SlashLoot doesn't specifically recognise. Off by default: leaving unknown modded containers to vanilla is the compatible choice.
- **`delegateContainerAnimation`** — forward open/close to the real container so lids animate, barrels open, and trapped chests emit redstone. Leave on unless a mod conflicts.
- **`playOpenCloseSounds`** — manual open sound. Only used when `delegateContainerAnimation` is `false` (with delegation on, vanilla plays it).
- **`cleanupOnBreak`** — drop a container's stored loot when it's destroyed.
- **`pruneIntervalTicks`** / **`pruneBatchSize`** — background sweep that reclaims entries for containers that no longer exist. `0` disables it.
- **`debugLogging`** — log one line per container with its position, loot table, and whether SlashLoot instanced it or fell back to vanilla, with the reason. Built for modpack compatibility testing.

Missing keys take their defaults, so a config from an older version keeps working. `/slashloot reload` re-reads the file without a restart.

## Admin commands

All require permission level 2 (op).

| Command | Effect |
|---|---|
| `/slashloot forget here` | Wipe every player's personal loot at the block you're looking at |
| `/slashloot forget at <x> <y> <z>` | Same, by coordinate |
| `/slashloot forget player <player>` | Wipe a player's personal loot at every container in the current dim |
| `/slashloot forget all` | Wipe every stored container in the current dimension |
| `/slashloot prune` | Drop stored entries whose container no longer exists |
| `/slashloot stats` | Stored entry counts for the current dimension |
| `/slashloot reload` | Re-read `config/slashlootr.json` |

## Known limitations

- **Hoppers don't pull from instanced containers.** The world container stays "unrolled" by design, so hoppers see an empty container. Player-placed containers are unaffected — and so are blocklisted ones, which behave exactly as in vanilla.
- **Comparator output reads 0** from those same containers, for the same reason.
- **Decorated pots and suspicious sand/gravel** are not covered — they use a different brushing mechanic.

## Source & links

- **GitHub** (source, issues, architecture deep-dive): https://github.com/slashdaemon/SlashLootr
- **Architecture / per-band drift docs**: https://github.com/slashdaemon/SlashLootr/blob/main/docs/ARCHITECTURE.md
- **License**: CC-BY-4.0
