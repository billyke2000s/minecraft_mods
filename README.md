# minecraft_mods

Resource pack for the **Server Flintlock** mod (Minecraft 1.21.1).

`flintlock-pack-v1.zip` is downloaded automatically by players when they join a world running the
Flintlock mod. It adds the 3D flintlock pistol and musket ball models. Don't rename or edit it:
the mod checks its SHA-1 (`fc240450e4a62ab0bb2a1a7271435909d56df3a2`). For a new version, add a new
file (e.g. `flintlock-pack-v2.zip`) and update the mod.

## Bedrock Tools (server-side mod)

`bedrock-tools/` is a server-only NeoForge 1.21.1 mod. Crafting the normal tool shapes with **bedrock**
instead of netherite gives unbreakable netherite tools with very high stats and a bedrock look; normal
netherite tools are unchanged. Players need nothing installed: the look comes from
`bedrocktools-pack-v1.zip`, which the mod sends when a player joins. Its models use Minecraft's own
bedrock texture (no copied textures). Change the tools in `bedrock-tools/tools/generate.py`, run it,
and commit what it writes; a new pack needs a new `PACK_VERSION` file name. Admins can give a full set
with `/loot give <player> loot bedrocktools:all_tools`. Every player gets the recipes in their recipe book on
join (greyed out without bedrock).

## Natives (server + client mod)

`natives/` is a NeoForge 1.21.1 mod that adds native tribes living in camps. Unlike the mods above it
adds a new mob, so players need it too; AutoModpack syncs it from the server's `mods` folder.

Each tribe belongs to a kind of landscape (works with vanilla and Moderner Beta biomes):

| Tribe | Biomes | Homes |
|---|---|---|
| Dunekin | deserts | sandstone huts, canvas roofs |
| Verdani | jungles / rainforests | jungle-wood huts, bamboo roofs |
| Frostmark | snow, ice, tundra | snow domes |
| Redgrass | savannas / shrublands | mud walls, thatched roofs |
| Pinemark | taigas | hide lodges, spruce roofs |

The tribes are loosely inspired by real regional dress and homes; names and details are invented.

Camps (a Village Heart, campfire, huts, a chief, warriors and villagers) generate in **newly explored**
chunks of those biomes. Ways to colonise a village:

- **Trade & alliance:** right-click a native with an emerald or the tribe's favourite item to trade.
  Each trade raises your trust; at 100 the village allies with you and joins your colony.
- **Conquest:** kill the chief and the village surrenders to you (this also takes villages from other players).
- **Claim territory:** right-click the Village Heart of a village you own with a **Colony Flag**
  (sticks, wool and an emerald) to protect 48 blocks around it from other players.
- **They may fight back:** hurting natives or wrecking their huts lowers trust, and the village rolls
  whether to take up arms (50% by default). Villages that hate you may send war parties.

Right-click a Village Heart to see its status. Commands: `/natives info`; for ops `/natives camp [tribe]`
(builds a camp at your feet) and `/natives villages`. Numbers live in `config/natives-common.toml`.
Textures come from `natives/tools/generate_textures.py` (needs Pillow); commit what it writes.
