#!/usr/bin/env python3
"""Draws the kippah/peyot texture (32x32). Run: python3 villager-kippah/tools/generate_texture.py"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "villagerkippah", "textures", "entity", "kippah.png")
NAVY = (28, 38, 92, 255)
NAVY_LIGHT = (44, 58, 128, 255)
BAND = (226, 220, 200, 255)
HAIR = (46, 30, 20, 255)
HAIR_LIGHT = (78, 54, 36, 255)


def main():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.putpixel
    # Kippah cap (texOffs 0,0; 5x1x5 -> u 0..20, v 0..6): velvet top, white embroidered band round the side.
    for x in range(20):
        for y in range(6):
            px((x, y), NAVY if (x + y) % 3 else NAVY_LIGHT)
    for x in range(20):
        px((x, 5), BAND)
    # Small crown (texOffs 0,6; 3x0.5x3 -> u 0..12, v 6..10).
    for x in range(12):
        for y in range(6, 10):
            px((x, y), NAVY_LIGHT if (x + y) % 2 else NAVY)
    # Peyot (texOffs 0,10 and 4,10; 1x7.5x1 -> each u 4 wide, v 10..19): curled dark hair.
    for base in (0, 4):
        for x in range(base, base + 4):
            for y in range(10, 20):
                px((x, y), HAIR_LIGHT if (y + x) % 3 == 0 else HAIR)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
