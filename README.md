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

## Whip (server + client mod)

`whip/` is a NeoForge 1.21.1 mod that adds a black braided leather **Whip** (Combat tab). Players need it
too; AutoModpack syncs it. 250 durability.

- **Right-click** throws the whip out to 7 blocks. Like a real bullwhip it starts thrown back, a loop
  rolls forward until the tip snaps straight, and it cracks *at the tip* (loud). Only what is still under
  the tip at that moment is struck. Then it goes slack and drops. The tip stops on anything in the way,
  including grass and flowers.
- **Left-click** hits up close with 2 blocks of extra reach.
- **A creature hit** loses half a heart and is jolted forward with a hop. 15% of the time the lash wraps
  their held item and yanks it out of their hand towards you.
- **Item frames, boats and minecarts** take the hit too (a frame drops its item).
- **Blocks at the tip:** buttons are pressed, levers flipped, pressure plates tripped for a second,
  bells rung, and grass, flowers and ferns cut (crops, saplings, berry bushes and nether wart are spared).
- **The crack itself** snags loose items at the tip and flicks them back to you, and spooks animals within
  6 blocks so they bolt away from the bang.

Recipe (shaped): leather on the diagonal from bottom-left to top-right, and string down the right-hand
column below the top corner:

```
. . L
. L S
L . S
```

The icon comes from `whip/tools/generate_textures.py` (needs Pillow) and the crack sound from
`whip/tools/generate_sound.py` (needs numpy and ffmpeg).
