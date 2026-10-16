# Chest Transporter for NeoForge and Fabric 1.21.1: development log

## Origin and scope (2026-10-16)

The idea comes from [Chest Transporter](https://www.curseforge.com/minecraft/mc-mods/chest-transporter) by cubex2,
last released for Minecraft 1.12.2. Its CurseForge page gives no license for reuse, so this mod takes only the idea:
the code, the model and the textures are written from scratch. The original was looked at only to understand the
feature set (tiers, debuffs, spawners, minecarts).

Decided with the owner:
- tiers from vanilla 1.21 materials: wood, copper, iron, gold, diamond, netherite (no tin, silver, obsidian);
- vanilla containers (chest, trapped chest, barrel, minecart with chest) and spawners; other mods' containers can be
  added to the block tag `chest_transporter:transportable` by packs, there is no code per mod;
- an own model: a hand truck with the load standing on its plate.

## Fabric (2026-10-16)

One repository for both loaders, after our Iron Backpacks MultiLoader setup: `common` (vanilla code on NeoForm, the
resources and the datagen output), `neoforge` and `fabric`. Decided with the owner: one repository, an own small
config instead of Forge Config API Port, protections through the block break event plus the vanilla build checks.
- `Platform` (found with `ServiceLoader`): registration, config folder, break check, place guard, sending packets;
  `ClientPlatform`: the frame model.
- Clicks: NeoForge `RightClickBlock` / `EntityInteract`, Fabric `UseBlockCallback` / `UseEntityCallback`, all calling
  `Transport.onUseBlock` and `MinecartTransport.onUseEntity`. Player ticks: NeoForge `PlayerTickEvent`, Fabric server
  tick over the players and the client tick for the local player.
- Config: the same `config/chest_transporter-server.toml` and keys as the NeoForge config of 1.0.0 (old files are read),
  a lenient reader that resets wrong types and clamps numbers, sent to each joining client (`chest_transporter:config`).
- Vanilla instead of NeoForge-only API: own quad loop instead of `renderModelLists` (private in vanilla), the vanilla
  `renderSingleBlock` and `getSoundType`, the vanilla block entity item renderer for chests on the truck.
- Fabric has no block place event: placing relies on `mayUseItemAt`/`mayInteract`, like vanilla blocks.
- Tests: the shared checks live in `common/src/selftest`, each loader wires them to its events (NeoForge
  `NeoForgeTestHooks`, Fabric a second dev-only mod `chest_transporter_selftest`); `tools/*.sh` take `LOADER=fabric`.

## Design

| Part | How |
| --- | --- |
| Load | data component `chest_transporter:carried`: block state + block entity data (`saveWithoutMetadata`), network codec with the vanilla 2 MiB NBT limit |
| Pick up | `onItemUseFirst`, so the chest is picked up instead of opened; break event for claim mods, `mayBuild`/`mayInteract` for spawn protection; locked and oversized loads refused; the container is emptied before removal so nothing drops; stale screens closed |
| Put down | `BlockPlaceContext` + the block's own `getStateForPlacement` (facing, double chests), place event with snapshot restore, then `loadWithComponents` |
| Double chests | only the clicked half is taken, as a single chest; the other half turns single through the vanilla shape update |
| Minecarts | `PlayerInteractEvent.EntityInteract`: items, loot table and name move between the minecart and the chest data; the loot table is cleared first so removing the minecart generates nothing |
| Carrying effects | transient attribute modifiers on both sides (movement speed −30 %, block break speed −50 %, jump strength −0.08 → 0.86 blocks), exhaustion 0.005 per tick on the server; the inventory is scanned only when its change counter moves (or once a second), one cache per side |
| Rendering | the item model is `builtin/entity`; `TransporterRenderer` draws the frame model (9 elements, 46 quads, hidden faces left out) and the load: baked block models through `renderSingleBlock`, chests through their item renderer with a cached item stack; frame models are cached until the next model bake |
| Art | `tools/gen_assets.py` draws one 16×16 texture per tier and writes the frame models and display transforms (tuned with the scene's `pose` mode) |
| Languages | `tools/gen_lang.py`, terms from the vanilla 1.21.1 translations |

## Verification

Every test below runs on both loaders (`LOADER=fabric tools/…`), with the same results.

- `tools/selftest.sh`: dev server, 26 checks — registries, durability per tier, recipes, repair, chest with items and
  name picked up and put down, facing, double chest halves and joining, barrel facing, loot table kept, spawner and its
  mob, spawner config and tier list, locked and protected chests, other blocks, size limit, no room, wooden truck
  breaking, open screens invalidated, minecart with chest both ways, item save/load, carrying effects on and off.
- `tools/scenetest.sh` (client): 6 screenshots, 9 checks — models, no magenta on screen in the inventory, in the hand,
  in third person, on the ground and in frames, tooltips, translations both ways. Passes in `en_us`, `ru_ru`, `de_de`,
  with Sodium, Iris, Lithium, FerriteCore, ModernFix and Jade, and on NeoForge 21.1.197.
- `tools/scenetest.sh -Pscene=stress`: 11 checks — broken server config (wrong types reset, numbers clamped), a chest
  of 27 full shulker boxes (579 KiB) to the client and back with all 729 stacks, 800 pick ups and put downs without
  losing an item, empty spawner tier list, leaving and rejoining with a loaded truck.
- `tools/nettest.sh`: dev client on a dev dedicated server, clicking like a player — 10 checks (pick up, client
  slowness, put down, opening the moved chest, spawner refused by the server config, minecart both ways).
- Release jars: NeoForge on dedicated NeoForge 21.1.197 and 21.1.259 servers (`prodtest/serve.sh`), Fabric on a
  production Fabric server with Fabric API (`prodtest/serve-fabric.sh`, Loom's `prodServer`).

## Performance (MacBook, dev client, 1600×900)

| Measured | Result |
| --- | --- |
| 128 loaded trucks as items and in item frames | NeoForge 222 FPS against 234, Fabric 221 FPS against 225 for the same 128 blocks without trucks |
| Carrying effects handler, per player per tick | NeoForge 0.9 µs loaded / 1.2 µs empty, Fabric 0.9 / 0.7 µs (limit in the test: 5 µs) |
| Server tick of a dev world, one player | 11.5 ms idle, 13.0 ms carrying (noise of chunk loading; the handler itself is the line above) |

Before the optimizations (inventory scan every tick, 54 quads, new item stack per frame) the same scene ran at 149 FPS
against a chest-only baseline of 213 FPS.
