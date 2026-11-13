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

## Six Shooter (rebuild for NeoForge 21.1)

`sixshooter/` is Tommy's Six Shooter revolver mod, rebuilt from its compiled 1.0.3 jar against NeoForge
21.1 so it loads on this server (the original was compiled against a different NeoForge and crashed
players on start-up and on join). Same items, ids, textures, sounds, recipes and behaviour; only the
build is fixed. Players need it too; AutoModpack syncs it.
