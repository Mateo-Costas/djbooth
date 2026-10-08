"""Compare two GUI screenshots and report where, and by how much, they differ.

Usage: diff_gui.py BEFORE AFTER OUT_DIFF.png
Prints the bounding boxes of the changed regions (merged when close together), so a change can be
checked against where it was meant to happen.
"""
import sys
from PIL import Image, ImageChops, ImageFilter

before = Image.open(sys.argv[1]).convert("RGB")
after = Image.open(sys.argv[2]).convert("RGB")
assert before.size == after.size, (before.size, after.size)

diff = ImageChops.difference(before, after).convert("L")
mask = diff.point(lambda v: 255 if v > 24 else 0)
total = sum(1 for v in mask.tobytes() if v)
print("size", before.size, "changed pixels", total, "= %.2f%% of the image" % (100 * total / (before.size[0] * before.size[1])))

# Merge nearby changes into regions: dilate, then flood the connected components.
merged = mask.filter(ImageFilter.MaxFilter(15))
w, h = merged.size
px = merged.load()
seen = [[False] * w for _ in range(h)]
regions = []
for y in range(h):
    for x in range(w):
        if px[x, y] and not seen[y][x]:
            stack = [(x, y)]
            seen[y][x] = True
            x0 = x1 = x
            y0 = y1 = y
            while stack:
                cx, cy = stack.pop()
                x0, x1, y0, y1 = min(x0, cx), max(x1, cx), min(y0, cy), max(y1, cy)
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny] and not seen[ny][nx]:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            regions.append((x0 + 7, y0 + 7, x1 - 7, y1 - 7))
regions.sort(key=lambda r: (r[1], r[0]))
for r in regions:
    print("region x %4d..%4d  y %4d..%4d  (%d x %d)" % (r[0], r[2], r[1], r[3], r[2] - r[0] + 1, r[3] - r[1] + 1))

# A picture of it: the after image, with the changed regions boxed in red.
out = after.copy()
from PIL import ImageDraw
d = ImageDraw.Draw(out)
for r in regions:
    d.rectangle((r[0] - 3, r[1] - 3, r[2] + 3, r[3] + 3), outline=(255, 40, 40), width=2)
out.save(sys.argv[3])
