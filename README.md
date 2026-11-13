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

## Flintlock Arms (client-side, for Server Flintlock 1.4.0)

`flintlock-arms/` draws your own arms in first person while you hold the Flintlock, posed like the arm
in the original Blockbench model (hand under the gun at the trigger, drawn at the model's 1.6x scale).
The hand is pinned to the gun in each animation frame while the shoulder stays put, so the arm pivots
naturally through the recoil, inspect and reload motions. During the reload your other hand does the work: it brings a
musket ball up and drops it into the muzzle (frames 7700-7707), grips the ramrod and rams it home
(7708-7722), then lets go and drops away. Visual only; players without it can
still join. AutoModpack syncs it.
