# SlashLoot

**Per-player loot for naturally-generated chests, barrels, shulker boxes, and minecarts — server-side only, no client install required.**

Every player who opens a naturally-generated container gets their own personal copy of the loot. Open a mineshaft chest and get your roll; your friend opens that same chest and gets a separate roll of their own. No more racing to dungeons, and nobody arrives to find an empty chest after someone else already cleared it out.

Containers you place yourself are never touched — chests, barrels, and shulker boxes you build behave exactly like vanilla.

## Server-side only — players install nothing

SlashLoot runs entirely on the server. Players connect with plain vanilla Fabric: they don't install the mod, and they don't have to update when you do. SlashLoot never replaces the block or its block-state, so the container always stays a real `minecraft:chest` (or barrel, shulker, minecart, etc.). Datapack predicates, `/data` queries, scoreboard selectors, and structure saves keep seeing ordinary vanilla blocks.

## Features

- **Per-player instanced loot** for naturally-generated containers. One open = one personal roll.
- **Persists across server restarts.** Each player's personal loot is saved per-dimension.
- **Stable on re-open.** Close the menu with items still inside and they're there next time you open it.
- **Player-placed containers untouched** — full vanilla behavior for anything you build.
- **Double-chest support.** Each half tracks per-player loot independently; opening either half shows a combined 54-slot view.
- **Configurable.** Per-dimension and per-loot-table blocklists for adventure-map compatibility.
- **Admin commands** to wipe personal loot when you need to.

## Container coverage

- Chests, trapped chests, barrels, shulker boxes
- Chest minecarts, hopper minecarts, chest boats
- Double chests (combined 54-slot view)

## Installation

1. Drop the JAR matching your Minecraft version into your server's `mods/` folder.
2. Drop the matching Fabric API version into the same folder.
3. Restart the server.

That's it. Clients connect with vanilla Fabric — nothing to install on their end.

## Supported versions

Pick the JAR that matches your server's Minecraft version, and use the listed Fabric API build.

| Minecraft | Fabric API      |
| --------- | --------------- |
| 1.20.1    | 0.92.2+1.20.1   |
| 1.20.5    | 0.97.8+1.20.5   |
| 1.21      | 0.102.0+1.21    |
| 1.21.1    | 0.115.6+1.21.1  |
| 1.21.2    | 0.106.1+1.21.2  |
| 1.21.4    | 0.114.0+1.21.4  |
| 1.21.6    | 0.128.1+1.21.6  |
| 1.21.9    | 0.134.1+1.21.9  |
| 1.21.11   | 0.141.2+1.21.11 |
| 26.1.2    | 0.146.1+26.1.2  |

## Configuration

A `config/slashlootr.json` file is created on first launch:

```json
{
  "dimensionBlocklist": [],
  "lootTableBlocklist": [],
  "playOpenCloseSounds": true
}
```

- **`dimensionBlocklist`** — dimensions (e.g. `"minecraft:the_nether"`) where SlashLoot is disabled and vanilla loot behavior applies.
- **`lootTableBlocklist`** — loot tables (e.g. `"minecraft:chests/buried_treasure"`) to skip. Useful for adventure maps with intentionally shared loot.
- **`playOpenCloseSounds`** — play the chest open sound when the personal menu opens.

## Admin commands

All require permission level 2 (op).

| Command                                  | Effect                                                              |
| ---------------------------------------- | ------------------------------------------------------------------ |
| `/slashloot forget here`                | Wipe every player's personal loot at the block you're looking at   |
| `/slashloot forget at <x> <y> <z>`      | Same, by coordinate                                                |
| `/slashloot forget player <player>`     | Wipe a player's personal loot at every container in the current dim |

## Known limitations

- **No chest lid animation.** The open sound plays, but the lid stays closed — the per-player menu bypasses vanilla's `ContainerOpenersCounter`.
- **Hoppers don't pull from naturally-generated chests.** The world container is left "unrolled" by design, so a hopper attached to one sees an empty container. Player-placed chests are unaffected.
- **Comparator output reads 0** from naturally-generated chests for the same reason.
- **Decorated pots and suspicious sand/gravel** are not covered — they use a different (brushing) mechanic.

## Source & links

- **GitHub** (source, issues, architecture deep-dive): https://github.com/mindfulent/SlashLootr
- **License**: CC-BY-4.0
