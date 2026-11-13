#!/usr/bin/env python3
"""Draws the Whip item texture (32x32): a black braided bullwhip coiled in a double loop, the stiff
handle running up to the top-right with a wrist strap, and a few loose tassel strips at the tip.

Run: python3 whip/tools/generate_textures.py  (needs Pillow). Commit the PNG it writes.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "whip", "textures", "item", "whip.png")
SIZE = 32

DARK = (14, 14, 16, 255)
MID = (34, 34, 38, 255)
LIGHT = (66, 66, 74, 255)
STRAP = (24, 24, 27, 255)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def put(x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img.putpixel((x, y), c)

    def braid(step):
        # Plait pattern: dark, mid, highlight.
        return (DARK, MID, LIGHT)[step % 3]

    def thick_point(x, y, r, step):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r + 0.5:
                    put(x + dx, y + dy, braid(step + dx + dy))

    # Double coil of thong: two loops of braided cord.
    for (cx, cy, rx, ry, r, phase) in ((12.5, 19.5, 10.5, 9.0, 1, 0), (13.5, 19.5, 6.5, 4.8, 1, 1)):
        steps = 140
        for i in range(steps):
            a = 2 * math.pi * i / steps
            x = int(round(cx + rx * math.cos(a)))
            y = int(round(cy + ry * math.sin(a)))
            thick_point(x, y, r, i // 3 + phase)

    # Handle: a stiff, thicker braided stock from the coil up to the top-right corner.
    x0, y0, x1, y1 = 19, 12, 29, 2
    n = 40
    for i in range(n + 1):
        t = i / n
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        for d in (-1, 0, 1):
            put(x + d, y, braid(i // 2 + d))
            put(x, y + d, braid(i // 2 + d + 1))
    # Knob at the butt and a wrist strap looping off it.
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (2, 0), (0, -1)):
        put(29 + dx, 1 + dy, DARK)
    for x, y in ((31, 3), (31, 4), (30, 5), (29, 5), (28, 4)):
        put(x, y, STRAP)

    # The fall and tassel strips hanging off the inner coil, like the reference.
    for x, y in ((19, 17), (20, 18), (21, 19), (21, 20)):
        put(x, y, DARK)
    tassels = [[(22, 21), (23, 23), (23, 25), (24, 27), (24, 28)],
               [(22, 21), (24, 22), (25, 24), (26, 26), (27, 27)],
               [(22, 21), (24, 21), (26, 22), (28, 23), (29, 24)]]
    for strip in tassels:
        for (xa, ya), (xb, yb) in zip(strip, strip[1:]):
            for i in range(9):
                t = i / 8
                put(int(round(xa + (xb - xa) * t)), int(round(ya + (yb - ya) * t)), MID)
        put(*strip[-1], LIGHT)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
