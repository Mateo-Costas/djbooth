"""Draws the block textures: cdj_top, cdj_side, mixer_top, mixer_side (64x64 each).

These used to be centre-crops of the same Pioneer press photos the GUI panel was built
from, which put a manufacturer's product photography and model badging on the side of a
Minecraft block. Drawn from primitives instead, in the same palette as the GUI panel and
the mod icon, so the deck reads as one object whether you are looking at the block in the
world or the panel in the screen.

Drawn at 8x and downsampled: the tops carry curves (the jog, the knob wells) that alias
badly at 64px, and Minecraft magnifies these with nearest-neighbour, so whatever is in the
file is exactly what a player sees on the block face.

Usage: python tools/gen_block_textures.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "common/src/main/resources/assets/soundsystem_dj/textures/block"

N = 64
SS = 8
S = N * SS

BODY = (38, 40, 47)
BODY_HI = (86, 91, 103)
BODY_LO = (18, 19, 24)
FACE = (30, 32, 38)
RECESS = (15, 16, 20)
INK = (110, 115, 126)
ACCENT = (37, 224, 192)
CUE = (255, 86, 68)
PLAY = (46, 200, 96)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def new_face(base=FACE):
    """A panel face, lit from the top edge like the rest of the mod's art."""
    img = Image.new("RGB", (S, S))
    d = ImageDraw.Draw(img)
    for y in range(S):
        d.rectangle([0, y, S, y + 1], fill=lerp(lerp(base, BODY_HI, 0.16), base, (y / S) ** 0.5))
    # Bevelled edge, so the block face has a rim instead of bleeding into its neighbour.
    d.rectangle([0, 0, S - 1, S - 1], outline=BODY_LO, width=SS)
    d.rectangle([0, 0, S - 1, SS], fill=lerp(BODY_HI, BODY, 0.3))
    return img, d


def knob(d, cx, cy, r, value=0.5):
    """A knob seen from above: well, cap, and a pointer at some position, because a panel
    full of centred knobs looks like a render and not like something anyone has used."""
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=lerp(BODY, BODY_HI, 0.4))
    d.ellipse([cx - r * 0.92, cy - r * 0.92, cx + r * 0.92, cy + r * 0.92], fill=RECESS)
    d.ellipse([cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7], fill=lerp(BODY, BODY_HI, 0.2))
    a = math.radians((value - 0.5) * 270)
    px, py = cx + math.sin(a) * r * 0.5, cy - math.cos(a) * r * 0.5
    d.ellipse([px - r * 0.16, py - r * 0.16, px + r * 0.16, py + r * 0.16], fill=INK)


def cdj_top():
    img, d = new_face()

    # Display, up at the back where the deck puts it.
    d.rounded_rectangle([S * 0.10, S * 0.08, S * 0.90, S * 0.33], radius=SS, fill=(10, 11, 14))
    d.rounded_rectangle([S * 0.12, S * 0.10, S * 0.88, S * 0.31], radius=SS, fill=(14, 16, 21))
    # A frozen waveform on it: a few bars of something, so the screen is not a black hole.
    for i in range(26):
        x = S * 0.14 + i * S * 0.028
        h = S * 0.085 * (0.35 + 0.65 * abs(math.sin(i * 0.9) * math.cos(i * 0.31)))
        col = ACCENT if i < 11 else lerp(ACCENT, (60, 70, 90), 0.72)
        d.rectangle([x, S * 0.205 - h, x + S * 0.017, S * 0.205 + h], fill=col)

    # Jog wheel.
    cx, cy, r = S * 0.5, S * 0.645, S * 0.265
    for i in range(360):
        t = (math.cos(math.radians(2 * (i - 35))) + 1) / 2
        d.pieslice([cx - r, cy - r, cx + r, cy + r], i, i + 2,
                   fill=lerp((40, 42, 50), (126, 132, 146), t ** 1.6))
    d.ellipse([cx - r * 0.9, cy - r * 0.9, cx + r * 0.9, cy + r * 0.9], fill=(24, 25, 31))
    for i in range(16):
        a = math.radians(i * 22.5)
        dx, dy = cx + r * 0.68 * math.cos(a), cy + r * 0.68 * math.sin(a)
        d.ellipse([dx - r * 0.05, dy - r * 0.05, dx + r * 0.05, dy + r * 0.05], fill=(16, 17, 21))
    d.ellipse([cx - r * 0.34, cy - r * 0.34, cx + r * 0.34, cy + r * 0.34], fill=(13, 14, 18))
    d.ellipse([cx - r * 0.14, cy - r * 0.14, cx + r * 0.14, cy + r * 0.14], fill=CUE)

    # Transport, bottom left; tempo fader, right edge.
    d.rounded_rectangle([S * 0.07, S * 0.74, S * 0.17, S * 0.83], radius=SS // 2, fill=lerp(CUE, BODY_LO, 0.4))
    d.rounded_rectangle([S * 0.07, S * 0.86, S * 0.17, S * 0.95], radius=SS // 2, fill=lerp(PLAY, BODY_LO, 0.4))
    d.rounded_rectangle([S * 0.87, S * 0.60, S * 0.92, S * 0.95], radius=SS // 2, fill=RECESS)
    d.rectangle([S * 0.855, S * 0.74, S * 0.935, S * 0.775], fill=lerp(BODY, BODY_HI, 0.6))
    return img


def mixer_top():
    img, d = new_face()

    # Two channel strips: knob column over a fader slot, the way the panel lays them out.
    for i, x in enumerate((0.28, 0.62)):
        cx = S * x
        d.rounded_rectangle([cx - S * 0.13, S * 0.05, cx + S * 0.13, S * 0.81], radius=SS, fill=(23, 25, 30))
        for j, v in enumerate((0.62, 0.5, 0.38, 0.5 + 0.2 * i)):
            knob(d, cx, S * (0.125 + j * 0.115), S * 0.052, v)
        # Channel fader, its travel kept inside the strip and parked somewhere sensible
        # rather than at zero.
        d.rounded_rectangle([cx - S * 0.020, S * 0.60, cx + S * 0.020, S * 0.78], radius=SS // 2, fill=RECESS)
        fy = S * (0.645 if i == 0 else 0.705)
        d.rounded_rectangle([cx - S * 0.055, fy, cx + S * 0.055, fy + S * 0.04],
                            radius=SS // 2, fill=lerp(BODY, BODY_HI, 0.55))

    # Master knob and the level meter, off to the right of the pair.
    knob(d, S * 0.88, S * 0.15, S * 0.058, 0.72)
    for i in range(9):
        y = S * (0.29 + i * 0.033)
        col = (46, 200, 96) if i < 5 else (240, 190, 60) if i < 7 else (230, 70, 60)
        lit = i < 6
        d.rectangle([S * 0.848, y, S * 0.912, y + S * 0.021],
                    fill=col if lit else lerp(col, RECESS, 0.82))

    # Crossfader across the bottom, clear of the channel strips above it.
    d.rounded_rectangle([S * 0.10, S * 0.875, S * 0.90, S * 0.915], radius=SS // 2, fill=RECESS)
    d.rounded_rectangle([S * 0.45, S * 0.855, S * 0.55, S * 0.935], radius=SS // 2,
                        fill=lerp(BODY, BODY_HI, 0.55))
    return img


def side(kind, led=ACCENT):
    """Chassis flank: brushed metal, cooling detail, and one status LED so the block has a
    front. The two blocks get different detail — a vent bank against horizontal ridges — so
    a deck and a mixer are still telling apart from the side, where neither has a face."""
    img = Image.new("RGB", (S, S))
    d = ImageDraw.Draw(img)
    for x in range(S):
        v = 26 + 5 * math.sin(x * 0.09) + 3 * math.sin(x * 0.31)
        d.rectangle([x, 0, x + 1, S], fill=(int(v), int(v) + 1, int(v) + 5))
    # Top lip catches the light; the foot sits in its own shadow.
    d.rectangle([0, 0, S, S * 0.055], fill=lerp(BODY_HI, BODY, 0.25))
    d.rectangle([0, S * 0.055, S, S * 0.085], fill=BODY_LO)
    d.rectangle([0, S * 0.93, S, S], fill=(12, 13, 17))
    if kind == "vents":
        for i in range(6):
            x = S * (0.16 + i * 0.115)
            d.rounded_rectangle([x, S * 0.32, x + S * 0.045, S * 0.72], radius=SS // 2, fill=(14, 15, 19))
            d.rounded_rectangle([x, S * 0.32, x + S * 0.045, S * 0.36], radius=SS // 2, fill=(9, 10, 13))
    else:
        for i in range(5):
            y = S * (0.30 + i * 0.10)
            d.rectangle([S * 0.12, y, S * 0.88, y + S * 0.045], fill=(15, 16, 21))
            d.rectangle([S * 0.12, y + S * 0.045, S * 0.88, y + S * 0.058],
                        fill=lerp(BODY, BODY_HI, 0.35))
    d.ellipse([S * 0.055, S * 0.16, S * 0.105, S * 0.21], fill=led)
    return img


def save(img, name):
    path = OUT_DIR / name
    img.resize((N, N), Image.LANCZOS).save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    save(cdj_top(), "cdj_top.png")
    save(mixer_top(), "mixer_top.png")
    save(side("vents", led=ACCENT), "cdj_side.png")
    save(side("ridges", led=CUE), "mixer_side.png")
