#!/usr/bin/env python3
"""Generates the Natives mod textures (entity skins, item and block icons).

Run from anywhere:  python3 natives/tools/generate_textures.py
Needs Pillow. Commit the PNGs it writes. Skins use the standard 64x64 player layout.
Clothing is loosely inspired by real regional dress (indigo desert robes, Arctic parkas, red savanna
cloth, blue northern tunics, woven jungle cotton) without copying any specific design.
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "natives", "textures")


def box(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a cube in the player-skin layout."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
        "ring": (u, v + d, 2 * d + 2 * w, h),
    }


HEAD = box(0, 0, 8, 8, 8)
HAT = box(32, 0, 8, 8, 8)
BODY = box(16, 16, 8, 12, 4)
R_ARM = box(40, 16, 4, 12, 4)
L_ARM = box(32, 48, 4, 12, 4)
R_LEG = box(0, 16, 4, 12, 4)
L_LEG = box(16, 48, 4, 12, 4)


class Painter:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def px(self, x, y, color, jitter=6):
        r, g, b = color[:3]
        a = color[3] if len(color) > 3 else 255
        j = self.rng.randint(-jitter, jitter) if jitter else 0
        self.img.putpixel((x, y), (max(0, min(255, r + j)), max(0, min(255, g + j)), max(0, min(255, b + j)), a))

    def rect(self, rect, color, jitter=6):
        x, y, w, h = rect
        for i in range(w):
            for j in range(h):
                self.px(x + i, y + j, color, jitter)

    def rows(self, part, start, end, color, jitter=6):
        """Paint rows [start, end) all the way round a part (and its top/bottom if they fall in range)."""
        x, y, w, h = part["ring"]
        for j in range(start, min(end, h)):
            for i in range(w):
                self.px(x + i, y + j, color, jitter)

    def stripe(self, part, row, color):
        self.rows(part, row, row + 1, color, 4)


def paint_head(p, skin, hair, eye=(40, 28, 20), long_hair=False):
    for face in ("top", "bottom", "right", "front", "left", "back"):
        p.rect(HEAD[face], skin, 4)
    p.rect(HEAD["top"], hair)
    p.rows(HEAD, 0, 2, hair)
    # sides and back covered further down
    for face, depth in (("right", 5 if long_hair else 3), ("left", 5 if long_hair else 3), ("back", 8 if long_hair else 6)):
        x, y, w, h = HEAD[face]
        p.rect((x, y, w, depth), hair)
    fx, fy, _, _ = HEAD["front"]
    # eyes
    for ex in (1, 5):
        p.px(fx + ex, fy + 4, (235, 235, 230), 0)
        p.px(fx + ex + 1, fy + 4, eye, 0)
    p.px(fx + 2, fy + 3, tuple(max(0, c - 20) for c in hair[:3]), 0)
    p.px(fx + 5, fy + 3, tuple(max(0, c - 20) for c in hair[:3]), 0)
    # nose and mouth
    nose = tuple(max(0, c - 18) for c in skin[:3])
    p.px(fx + 3, fy + 5, nose, 0)
    p.px(fx + 4, fy + 5, nose, 0)
    mouth = tuple(max(0, c - 45) for c in skin[:3])
    p.px(fx + 3, fy + 6, mouth, 0)
    p.px(fx + 4, fy + 6, mouth, 0)


def paint_body(p, cloth, trim=None, trim_rows=(), belt=None):
    p.rect(BODY["top"], cloth)
    p.rect(BODY["bottom"], cloth)
    p.rows(BODY, 0, 12, cloth)
    for row in trim_rows:
        p.stripe(BODY, row, trim)
    if belt:
        p.stripe(BODY, 8, belt)


def paint_arms(p, cloth, skin, sleeve_rows=6, cuff=None):
    for arm in (R_ARM, L_ARM):
        p.rect(arm["top"], cloth)
        p.rect(arm["bottom"], skin)
        p.rows(arm, 0, sleeve_rows, cloth)
        p.rows(arm, sleeve_rows, 12, skin, 4)
        if cuff and sleeve_rows < 12:
            p.stripe(arm, sleeve_rows - 1, cuff)


def paint_legs(p, cloth, shoe, cloth_rows=9, skin=None, hem=None):
    for leg in (R_LEG, L_LEG):
        p.rect(leg["top"], cloth)
        p.rect(leg["bottom"], shoe)
        p.rows(leg, 0, cloth_rows, cloth)
        if skin and cloth_rows < 10:
            p.rows(leg, cloth_rows, 10, skin, 4)
        p.rows(leg, 10, 12, shoe)
        if hem:
            p.stripe(leg, cloth_rows - 1, hem)


def hat_ring(p, rows, color, front=True):
    """Paint rows of the hat (overlay) layer: a band around the head."""
    for face in ("right", "left", "back") + (("front",) if front else ()):
        x, y, w, h = HAT[face]
        for j in rows:
            for i in range(w):
                p.px(x + i, y + j, color, 5)


# ------------------------------------------------------------------------------------------------
SKINS = {
    "dunekin": [(141, 95, 62), (118, 80, 52)],
    "verdani": [(150, 101, 66), (126, 84, 55)],
    "frostmark": [(178, 132, 98), (158, 114, 82)],
    "redgrass": [(92, 58, 38), (72, 45, 30)],
    "pinemark": [(226, 186, 152), (206, 164, 130)],
}
HAIR = [(28, 22, 18), (46, 32, 24)]


def dunekin(p, role, v):
    skin = SKINS["dunekin"][v]
    indigo = (36, 46, 112)
    robe = (232, 226, 210) if role == "chief" else ((52, 66, 140) if role == "villager" else indigo)
    paint_head(p, skin, HAIR[v])
    paint_body(p, robe, (214, 170, 60) if role == "chief" else (110, 120, 190), (0, 11))
    paint_arms(p, robe, skin, 10)
    paint_legs(p, robe, (120, 86, 52), 10)
    # indigo headwrap; warriors and the chief also veil the lower face
    hat_ring(p, range(0, 3), indigo)
    p.rect(HAT["top"], indigo)
    if role != "villager":
        fx, fy, _, _ = HAT["front"]
        for i in range(8):
            for j in (5, 6, 7):
                p.px(fx + i, fy + j, indigo, 5)
        hat_ring(p, range(5, 8), indigo, front=False)


def verdani(p, role, v):
    skin = SKINS["verdani"][v]
    green = (64, 112, 54) if role != "warrior" else (88, 74, 46)
    paint_head(p, skin, HAIR[v])
    fx, fy, _, _ = HEAD["front"]
    for i in range(8):  # straight fringe
        p.px(fx + i, fy + 2, HAIR[v], 3)
    paint_body(p, green, (176, 52, 40), (3, 10), belt=(214, 176, 72))
    paint_arms(p, skin, skin, 0)
    for arm in (R_ARM, L_ARM):  # woven arm bands
        p.stripe(arm, 2, (176, 52, 40))
    paint_legs(p, green, (98, 70, 44), 6, skin)
    if role == "chief":
        # seed-bead necklace and woven shoulder cloth
        x, y, w, h = BODY["front"]
        for i in range(1, 7):
            p.px(x + i, y + 1, (190, 40, 36), 0)
        p.px(x + 3, y + 2, (232, 200, 80), 0)
        p.px(x + 4, y + 2, (232, 200, 80), 0)
        hat_ring(p, range(2, 3), (176, 52, 40))


def frostmark(p, role, v):
    skin = SKINS["frostmark"][v]
    parka = (226, 214, 190) if role == "chief" else ((112, 82, 54) if role == "villager" else (82, 60, 42))
    fur = (236, 234, 228)
    paint_head(p, skin, HAIR[v])
    paint_body(p, parka, (150, 40, 40), (10,), belt=None)
    paint_arms(p, parka, (70, 52, 36), 10, fur)
    paint_legs(p, (66, 50, 38), (200, 190, 170), 9)
    # fur-trimmed hood framing the face
    p.rect(HAT["top"], parka)
    hat_ring(p, range(0, 8), parka, front=False)
    fx, fy, _, _ = HAT["front"]
    for i in range(8):
        p.px(fx + i, fy, fur, 3)
        p.px(fx + i, fy + 1, fur, 3)
    for j in range(2, 8):
        p.px(fx, fy + j, fur, 3)
        p.px(fx + 7, fy + j, fur, 3)
    if role == "chief":
        x, y, w, h = BODY["front"]
        for i in range(8):
            p.px(x + i, y + 6, (40, 90, 160) if i % 2 else (190, 50, 40), 0)


def redgrass(p, role, v):
    skin = SKINS["redgrass"][v]
    red = (178, 32, 30) if role != "chief" else (40, 46, 128)
    check = (120, 18, 20) if role != "chief" else (24, 26, 80)
    paint_head(p, skin, (20, 16, 14))
    # short hair: trim the long sides back
    for face in ("right", "left", "back"):
        x, y, w, h = HEAD[face]
        p.rect((x, y + 2, w, 2), skin, 4)
    paint_body(p, red)
    x, y, w, h = BODY["ring"]
    for i in range(w):  # checked cloth
        for j in range(h):
            if (i // 2 + j // 2) % 2 == 0:
                p.px(x + i, y + j, check, 4)
    # beaded collar
    beads = [(230, 230, 220), (220, 60, 40), (40, 110, 200), (240, 190, 50)]
    bx, by, _, _ = BODY["front"]
    rows = 3 if role == "chief" else 1
    for j in range(rows):
        for i in range(8):
            p.px(bx + i, by + j, beads[(i + j) % 4], 0)
    paint_arms(p, skin, skin, 0)
    for arm in (R_ARM, L_ARM):
        p.stripe(arm, 6, beads[1])
    paint_legs(p, red, (90, 60, 40), 7, skin)


def pinemark(p, role, v):
    skin = SKINS["pinemark"][v]
    blue = (34, 58, 150) if role != "warrior" else (30, 42, 96)
    paint_head(p, skin, (92, 66, 40) if v == 0 else (60, 44, 30))
    paint_body(p, blue, None, (), belt=(40, 30, 24))
    for row, col in ((0, (200, 40, 36)), (1, (230, 190, 60)), (11, (200, 40, 36)), (10, (230, 190, 60))):
        p.stripe(BODY, row, col)
    paint_arms(p, blue, skin, 11, (200, 40, 36))
    paint_legs(p, (70, 56, 44), (120, 92, 60), 9)
    if role == "chief":
        # tall blue cap with a red band
        p.rect(HAT["top"], blue)
        hat_ring(p, range(0, 2), (200, 40, 36))
        hat_ring(p, range(2, 3), (230, 190, 60))


TRIBES = {"dunekin": dunekin, "verdani": verdani, "frostmark": frostmark, "redgrass": redgrass, "pinemark": pinemark}
ROLES = ["villager", "warrior", "chief"]


def write(img, *parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def skins():
    for tribe, fn in TRIBES.items():
        for role in ROLES:
            for v in range(2):
                p = Painter(f"{tribe}-{role}-{v}")
                fn(p, role, v)
                write(p.img, "entity", "native", f"{tribe}_{role}_{v}.png")


def colony_flag():
    p = Painter("flag")
    pole = (110, 78, 46)
    for y in range(1, 16):
        p.px(4, y, pole, 3)
        p.px(5, y, (90, 62, 36), 3)
    p.px(4, 0, (230, 190, 60), 0)
    p.px(5, 0, (230, 190, 60), 0)
    for x in range(6, 15):
        for y in range(1, 9):
            wave = 1 if (x // 3) % 2 else 0
            if y + wave < 9:
                p.px(x, y + wave, (40, 110, 60) if y < 5 else (230, 230, 220), 6)
    write(p.img, "item", "colony_flag.png")


def heart_block():
    side = Painter("heart-side")
    wood = (122, 88, 56)
    for x in range(16):
        for y in range(16):
            side.px(x, y, wood if x % 4 else (102, 72, 44), 8)
    # carved bands and an inlaid stone diamond
    for x in range(16):
        side.px(x, 2, (80, 56, 34), 3)
        side.px(x, 13, (80, 56, 34), 3)
    for i in range(4):
        for x in range(7 - i, 9 + i):
            side.px(x, 4 + i, (176, 170, 160), 6)
            side.px(x, 11 - i, (176, 170, 160), 6)
    side.px(7, 7, (60, 140, 120), 0)
    side.px(8, 8, (60, 140, 120), 0)
    write(side.img, "block", "village_heart_side.png")
    top = Painter("heart-top")
    for x in range(16):
        for y in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            top.px(x, y, (150, 112, 72) if int(d) % 3 else (110, 80, 50), 6)
    write(top.img, "block", "village_heart_top.png")


if __name__ == "__main__":
    skins()
    colony_flag()
    heart_block()
    print("textures written to", os.path.normpath(ROOT))
