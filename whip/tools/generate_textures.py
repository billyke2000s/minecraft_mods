#!/usr/bin/env python3
"""Draws the Whip item icon (16x16, hotbar size): a black braided bullwhip - short handle bottom-left,
the lash curling up in a loop and falling away to a tasselled tip at the bottom-right.

Black leather vanishes on dark slots, so the leather is drawn in near-black greys with braid
highlights, and the whole shape gets a pure-black outline (like vanilla's dark items).

Run: python3 whip/tools/generate_textures.py  (needs Pillow). Commit the PNG it writes.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "whip", "textures", "item", "whip.png")

OUTLINE = (8, 8, 10, 255)
HANDLE = [(52, 46, 44, 255), (78, 70, 66, 255)]      # braided grip, slightly warm
POMMEL = (96, 88, 82, 255)
LASH = [(52, 52, 58, 255), (100, 100, 112, 255)]        # braided thong
TIP = (112, 112, 122, 255)

# Handle: two pixels thick, bottom-left up towards the centre.
HANDLE_PX = [(1, 14), (2, 13), (3, 12), (4, 11), (5, 10),
             (2, 14), (3, 13), (4, 12), (5, 11), (6, 10)]
POMMEL_PX = [(1, 15), (0, 14)]
# Lash: up out of the handle, round a loop overhead, then falling away to the tip.
LASH_PX = [(6, 9), (7, 8), (7, 7), (6, 6), (5, 5), (5, 4), (6, 3), (7, 2), (8, 2), (9, 2), (10, 2),
           (11, 3), (12, 4), (12, 5), (12, 6), (11, 7), (11, 8), (12, 9), (13, 10), (13, 11)]
TIP_PX = [(14, 12), (14, 13), (13, 13), (15, 12)]


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    body = {}
    for p in HANDLE_PX:
        body[p] = HANDLE[(p[0] + p[1]) % 2]
    for p in POMMEL_PX:
        body[p] = POMMEL
    for i, p in enumerate(LASH_PX):
        body[p] = LASH[i % 2]
    for p in TIP_PX:
        body[p] = TIP

    # Outline: every empty neighbour (including diagonals) of the shape becomes black.
    for (x, y) in list(body):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in body and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    img.putpixel(q, OUTLINE)
    for (x, y), c in body.items():
        img.putpixel((x, y), c)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
