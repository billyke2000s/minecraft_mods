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

`whip/` is a NeoForge 1.21.1 mod that adds a black braided leather **Whip** (Combat tab). A lash deals
half a heart and jolts the target forward in the direction you're facing with a little hop, with a
loud crack and a rendered leather lash that flies out from your hand and snaps back. Left-click reaches 2 blocks further than a normal hit; **right-click
cracks the whip out to 7 blocks** and lashes the first creature or player in the way. 250 durability.
Players need it too; AutoModpack syncs it.

Recipe (shaped): leather on the diagonal from bottom-left to top-right, and string down the right-hand
column below the top corner:

```
. . L
. L S
L . S
```

The texture comes from `whip/tools/generate_textures.py` (needs Pillow) and the crack sound from
`whip/tools/generate_sound.py` (needs numpy and ffmpeg).
