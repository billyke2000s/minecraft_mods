"""Generates everything derived from the tool definitions below. Run from bedrock-tools/:

    python3 tools/generate.py

Writes the recipes and loot table into src/main/resources, the resource pack to
../bedrocktools-pack-v<PACK_VERSION>.zip (hosted in this repo like the Flintlock pack) and
PackInfo.java with that pack's URL and SHA-1. Output is deterministic; CI regenerates and diffs it.

The pack contains no copied textures: each tool model is built from 1-pixel elements textured with
Minecraft's own minecraft:block/bedrock (head) and minecraft:block/dark_oak_log (handle).
"""
import hashlib, io, json, os, zipfile

PACK_VERSION = 1
RAW = "https://raw.githubusercontent.com/billyke2000s/minecraft_mods/main/"

MODEL_ID = 7301  # custom_model_data the resource pack switches on

def name(text):
    # 1.21.1 item_name is a JSON text component stored as a string
    return '{"text":"%s","color":"dark_gray","italic":false}' % text

def attr(kind, amount, ident):
    return {"type": "minecraft:" + kind, "id": "bedrocktools:" + ident, "amount": amount,
            "operation": "add_value", "slot": "mainhand"}

REACH = [attr("player.block_interaction_range", 3, "block_reach"),
         attr("player.entity_interaction_range", 2, "entity_reach")]

def tool_rule(tag, speed=150.0):
    # First rule: never drop-correct for blocks netherite cannot mine (e.g. bedrock stays unbreakable).
    return {"rules": [{"blocks": "#minecraft:incorrect_for_netherite_tool", "correct_for_drops": False},
                      {"blocks": "#minecraft:" + tag, "speed": speed, "correct_for_drops": True}],
            "default_mining_speed": 1.0, "damage_per_block": 0}

# Player base: 1 attack damage, 4 attack speed. Totals: sword 100 dmg / 20 speed etc.
TOOLS = {
    "sword": dict(item="netherite_sword", label="Bedrock Sword",
                  pattern=[" B ", " B ", " S "],
                  enchant={"sharpness": 10, "looting": 5, "fire_aspect": 2, "sweeping_edge": 3},
                  attrs=[attr("generic.attack_damage", 99, "damage"), attr("generic.attack_speed", 16, "speed")],
                  tool=None),
    "pickaxe": dict(item="netherite_pickaxe", label="Bedrock Pickaxe",
                    pattern=["BBB", " S ", " S "],
                    enchant={"efficiency": 10, "fortune": 5},
                    attrs=[attr("generic.attack_damage", 19, "damage"), attr("generic.attack_speed", 4, "speed")],
                    tool=tool_rule("mineable/pickaxe")),
    "axe": dict(item="netherite_axe", label="Bedrock Axe",
                pattern=["BB ", "BS ", " S "],
                enchant={"sharpness": 10, "efficiency": 10, "fortune": 5},
                attrs=[attr("generic.attack_damage", 59, "damage"), attr("generic.attack_speed", 0, "speed")],
                tool=tool_rule("mineable/axe")),
    "shovel": dict(item="netherite_shovel", label="Bedrock Shovel",
                   pattern=[" B ", " S ", " S "],
                   enchant={"efficiency": 10, "fortune": 5},
                   attrs=[attr("generic.attack_damage", 19, "damage"), attr("generic.attack_speed", 4, "speed")],
                   tool=tool_rule("mineable/shovel")),
    "hoe": dict(item="netherite_hoe", label="Bedrock Hoe",
                pattern=["BB ", " S ", " S "],
                enchant={"efficiency": 10, "fortune": 5},
                attrs=[attr("generic.attack_damage", 19, "damage"), attr("generic.attack_speed", 8, "speed")],
                tool=tool_rule("mineable/hoe")),
}

def components(t):
    c = {
        "minecraft:item_name": name(t["label"]),
        "minecraft:unbreakable": {"show_in_tooltip": True},
        "minecraft:custom_model_data": MODEL_ID,
        "minecraft:rarity": "epic",
        "minecraft:enchantment_glint_override": False,
        "minecraft:enchantments": {"levels": {"minecraft:" + k: v for k, v in t["enchant"].items()}, "show_in_tooltip": True},
        "minecraft:attribute_modifiers": {"modifiers": t["attrs"] + REACH, "show_in_tooltip": True},
    }
    if t["tool"]:
        c["minecraft:tool"] = t["tool"]
    return c

SPRITES = {
"sword": [
"................",
"............OBB.",
"...........OBBBO",
"..........OBBBO.",
".........OBBBO..",
"........OBBBO...",
".......OBBBO....",
"..O...OBBBO.....",
"..OO.OBBBO......",
"...OOBBBO.......",
"....OBBO........",
"....HOO.........",
"...HHOOO........",
"..HH...O........",
".HH.............",
"................"],
"pickaxe": [
"................",
".....OOOOOO.....",
"...OOBBBBBBO....",
"..OBBBBBBBBBO...",
"..OBOO...OBBBO..",
"...O....HOOBBO..",
".......HH..OBBO.",
"......HH....OBO.",
".....HH.....OBO.",
"....HH......OBO.",
"...HH.......OO..",
"..HH............",
".HH.............",
".H..............",
"................",
"................"],
"axe": [
"................",
".....OOOOOOOO...",
"....OBBBBBBBBHH.",
"...OBBBBBBBBHH..",
"...OBBBBBBBHH...",
"...OBBBBBBHH....",
"...OBBBBBHH.....",
"...OBBBBHH......",
"....OBBHH.......",
".....OHH........",
".....HH.........",
"....HH..........",
"...HH...........",
"..HH............",
".HH.............",
"................"],
"shovel": [
"................",
"...........OOO..",
"..........OBBBO.",
".........OBBBBO.",
".........OBBBBO.",
"..........OBBO..",
".........HHOO...",
"........HH......",
".......HH.......",
"......HH........",
".....HH.........",
"....HH..........",
"...HH...........",
"..HH............",
".HH.............",
"................"],
"hoe": [
"................",
"......OOOOO.....",
".....OBBBBBO....",
"......OOOBBO....",
"........HOBO....",
".......HH.O.....",
"......HH........",
".....HH.........",
"....HH..........",
"...HH...........",
"..HH............",
".HH.............",
".H..............",
"................",
"................",
"................"],
}

HEAD_TEXTURE = "minecraft:block/bedrock"
HANDLE_TEXTURE = "minecraft:block/dark_oak_log"

def elements(rows):
    """One element per horizontal run of same-material pixels, flat like a vanilla item (z 7.5-8.5)."""
    out = []
    for y, row in enumerate(rows):
        x = 0
        while x < 16:
            ch = row[x]
            if ch == ".":
                x += 1; continue
            mat = "handle" if ch == "H" else "head"
            x1 = x
            while x1 < 16 and row[x1] != "." and (("handle" if row[x1] == "H" else "head") == mat):
                x1 += 1
            # Outline ('O') pixels are part of the head and use the bedrock texture as it is.
            uv = [x, y, x1, y + 1]
            face = {"texture": "#" + mat, "uv": uv}
            out.append({"from": [x, 15 - y, 7.5], "to": [x1, 16 - y, 8.5],
                        "faces": {"south": dict(face), "north": {"texture": "#" + mat, "uv": [x1, y, x, y + 1]},
                                  "up": dict(face), "down": dict(face), "east": dict(face), "west": dict(face)}})
            x = x1
    return out

# Vanilla handheld item transforms, so the tools are held exactly like normal tools.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}

def dump(data):
    return json.dumps(data, indent=2, sort_keys=False) + "\n"

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)

def pack_bytes():
    files = {"pack.mcmeta": dump({"pack": {"pack_format": 34, "description": "Bedrock Tools textures"}})}
    for key, t in TOOLS.items():
        rows = SPRITES[key]
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), key
        item = t["item"]
        files[f"assets/minecraft/models/item/{item}.json"] = dump({
            "parent": "minecraft:item/handheld", "textures": {"layer0": f"minecraft:item/{item}"},
            "overrides": [{"predicate": {"custom_model_data": MODEL_ID}, "model": f"bedrocktools:item/bedrock_{key}"}]})
        files[f"assets/bedrocktools/models/item/bedrock_{key}.json"] = dump({
            "gui_light": "front",
            "textures": {"head": HEAD_TEXTURE, "handle": HANDLE_TEXTURE, "particle": HEAD_TEXTURE},
            "elements": elements(rows), "display": DISPLAY})
    buf = io.BytesIO()
    # Stored, not deflated: compressed bytes vary between zlib versions, which would change the SHA-1.
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as z:
        for path in sorted(files):
            info = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 3
            info.external_attr = 0o644 << 16
            z.writestr(info, files[path])
    return buf.getvalue()

def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    data = os.path.join(here, "src/main/resources/data/bedrocktools")
    entries = []
    for key, t in TOOLS.items():
        write(f"{data}/recipe/bedrock_{key}.json", dump({
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": t["pattern"],
            "key": {"B": {"item": "minecraft:bedrock"}, "S": {"item": "minecraft:stick"}},
            "result": {"id": "minecraft:" + t["item"], "count": 1, "components": components(t)}}))
        entries.append({"type": "minecraft:item", "name": "minecraft:" + t["item"],
                        "functions": [{"function": "minecraft:set_components", "components": components(t)}]})
    write(f"{data}/loot_table/all_tools.json", dump({"type": "minecraft:command",
          "pools": [{"rolls": 1, "entries": [e]} for e in entries]}))
    # Unlock all five recipes in every player's recipe book as soon as they join (shown greyed out:
    # nobody has bedrock). Recipe advancements have no display, so only the "New Recipes" toast shows.
    write(f"{data}/advancement/recipes/bedrock_tools.json", dump({
        "parent": "minecraft:recipes/root",
        "criteria": {"joined": {"trigger": "minecraft:tick"}},
        "requirements": [["joined"]],
        "rewards": {"recipes": [f"bedrocktools:bedrock_{key}" for key in TOOLS]}}))
    pack = pack_bytes()
    name = f"bedrocktools-pack-v{PACK_VERSION}.zip"
    with open(os.path.join(here, "..", name), "wb") as f:
        f.write(pack)
    sha1 = hashlib.sha1(pack).hexdigest()
    write(os.path.join(here, "src/main/java/com/billyke2000s/bedrocktools/PackInfo.java"),
          "package com.billyke2000s.bedrocktools;\n\n"
          "/** Generated by tools/generate.py - do not edit. */\n"
          "final class PackInfo {\n    private PackInfo() {}\n"
          f'    static final String URL = "{RAW}{name}";\n'
          f'    static final String SHA1 = "{sha1}";\n}}\n')
    print(name, sha1)

if __name__ == "__main__":
    main()
