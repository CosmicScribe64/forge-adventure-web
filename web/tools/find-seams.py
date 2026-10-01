"""Finds thin bright horizontal or vertical lines in screenshots of a tile map. Such a line is a
row (or column) of pixels much brighter than the rows on both sides, over a long stretch, which is
what texture bleeding between tiles looks like (the "white lines" in the cave). Check dark seams
(the lines across TenPatch buttons) by eye, because at a threshold low enough to catch them, the
UI's own one-pixel borders match too.

Usage: python3 web/tools/find-seams.py out/seams/*.png
Prints each suspicious line: file, orientation, position, length of the bright run.
"""
import sys

from PIL import Image


def luma(p):
    return (p[0] * 299 + p[1] * 587 + p[2] * 114) // 1000


def scan(path, min_run=40, jump=60):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = img.load()
    found = []
    # Rows: pixel much brighter than the pixels 2 above and 2 below (a 1-2 px line).
    for y in range(2, h - 2):
        run = best = 0
        for x in range(w):
            c = luma(px[x, y])
            if c - luma(px[x, y - 2]) > jump and c - luma(px[x, y + 2]) > jump:
                run += 1
                best = max(best, run)
            else:
                run = 0
        if best >= min_run:
            found.append(("row", y, best))
    for x in range(2, w - 2):
        run = best = 0
        for y in range(h):
            c = luma(px[x, y])
            if c - luma(px[x - 2, y]) > jump and c - luma(px[x + 2, y]) > jump:
                run += 1
                best = max(best, run)
            else:
                run = 0
        if best >= min_run:
            found.append(("column", x, best))
    return found


if __name__ == "__main__":
    total = 0
    for path in sys.argv[1:]:
        lines = scan(path)
        total += len(lines)
        for kind, pos, run in lines:
            print(f"{path}: {kind} {pos}: bright for {run} px")
    print(f"{total} suspicious lines in {len(sys.argv) - 1} screenshots")
