#!/usr/bin/env python3
"""Draws the Whip item texture: a black braided bullwhip with a looped lash and a tasselled tip.

Run: python3 whip/tools/generate_textures.py  (needs Pillow). Commit the PNG it writes.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "whip", "textures", "item", "whip.png")

BLACK = (18, 18, 20, 255)
BRAID = (40, 40, 45, 255)
SHINE = (78, 78, 86, 255)
WRIST = (30, 30, 34, 255)


# Hand-drawn 16x16: braided handle top-right, a loose coil of thong, three tassel strips at the tip.
# K black leather, B braid, S highlight, W wrist loop.
ART = [
    "..............KW",
    ".............SKW",
    "............SKB.",
    "...........SKB..",
    "..........SKB...",
    "...KKBKK.SKB....",
    "..B.....KKB.....",
    ".K......BK......",
    ".K.......K......",
    "K.........K.....",
    "B.........K.....",
    ".K.......K.B....",
    ".B......K...B.B.",
    "..KBKKBK...B.B.B",
    "...........B.B..",
    "..........B.....",
]
COLOURS = {"K": BLACK, "B": BRAID, "S": SHINE, "W": WRIST}


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            if ch in COLOURS:
                img.putpixel((x, y), COLOURS[ch])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
