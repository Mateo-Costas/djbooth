"""Draws the mod icon: soundsystem_dj_logo.png, 512x512.

Everything here is drawn from primitives. The previous icon was built from a Pioneer
press photo with the Pioneer DJ logo visible on the jog, which is someone else's
trademark on someone else's photograph — not something to ship, and a poor thing to
have sitting in a moderation queue. It also still read "DJ BOOTH", a name the mod
dropped.

Sized for where it is actually seen: Modrinth's search grid renders icons around
48px, so the design has to survive being shrunk to a thumbnail. That rules out a
wordmark (unreadable), fine detail (mush), and low contrast (invisible). One strong
silhouette, one accent colour.

Usage: python tools/gen_logo.py [variant]
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/soundsystem_dj_logo.png"
S = 512
SS = 4  # supersample: draw big, shrink down, get antialiasing for free

BG_EDGE = (10, 10, 14)
BG_MID = (26, 27, 34)
BODY = (44, 46, 54)
BODY_HI = (96, 100, 112)
PLATTER = (32, 33, 40)
ACCENT = (37, 224, 192)   # the mod's teal, used on the GUI too
CUE = (255, 86, 68)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def backdrop(img):
    """Radial falloff so the centre lifts off the panel instead of sitting flat on it."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width, img.height], fill=BG_EDGE)
    c = img.width / 2
    steps = 90
    for i in range(steps, 0, -1):
        t = i / steps
        r = c * 1.15 * t
        d.ellipse([c - r, c - r, c + r, c + r], fill=lerp(BG_MID, BG_EDGE, t))


def brushed_ring(d, cx, cy, r_out, r_in, base, hi, lobes=2, phase=35):
    """A metal rim: brightness sweeps around the circumference, so it reads as a
    machined edge catching light rather than a flat donut."""
    steps = 2000
    span = 360 / steps
    for i in range(steps):
        a = i * span
        t = (math.cos(math.radians(lobes * (a - phase))) + 1) / 2
        d.pieslice([cx - r_out, cy - r_out, cx + r_out, cy + r_out],
                   a, a + span * 1.6, fill=lerp(base, hi, t ** 1.7))
    d.ellipse([cx - r_in, cy - r_in, cx + r_in, cy + r_in], fill=PLATTER)


def bloom(img, radius, strength):
    """Blur a copy and screen it back over the original, so bright areas bleed light."""
    blurred = img.filter(ImageFilter.GaussianBlur(radius))
    return ImageChops.screen(img, blurred.point(lambda v: int(v * strength)))


def jog(d, cx, cy, r):
    """The jog wheel: rim, platter, dimple ring, centre display."""
    # Accent halo sits just outside the rim.
    d.ellipse([cx - r * 1.045, cy - r * 1.045, cx + r * 1.045, cy + r * 1.045], fill=ACCENT)
    brushed_ring(d, cx, cy, r, r * 0.80, BODY, BODY_HI)

    # Platter face, very slightly domed.
    for i in range(24):
        t = i / 24
        rr = r * 0.80 * (1 - t * 0.02)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=lerp((40, 42, 50), PLATTER, t))

    # Dimples around the platter, the detail that says "jog" and not "record".
    rd = r * 0.62
    dot = r * 0.038
    for i in range(20):
        a = math.radians(i * 18)
        px, py = cx + rd * math.cos(a), cy + rd * math.sin(a)
        d.ellipse([px - dot, py - dot, px + dot, py + dot], fill=(20, 21, 26))

    # Centre display with a cue marker.
    rc = r * 0.34
    d.ellipse([cx - rc, cy - rc, cx + rc, cy + rc], fill=(16, 17, 22))
    d.ellipse([cx - rc * 0.93, cy - rc * 0.93, cx + rc * 0.93, cy + rc * 0.93], fill=(22, 24, 30))
    rp = rc * 0.42
    d.ellipse([cx - rp, cy - rp, cx + rp, cy + rp], fill=CUE)

    # Position marker on the rim: the wheel is turned to somewhere, not parked.
    a = math.radians(-58)
    mr, mw = r * 0.90, r * 0.05
    mx, my = cx + mr * math.cos(a), cy + mr * math.sin(a)
    d.ellipse([mx - mw, my - mw, mx + mw, my + mw], fill=ACCENT)


def faders(d, x, y0, y1, w, n=3):
    """Channel faders, to say mixer rather than turntable."""
    gap = w * 2.4
    for i in range(n):
        fx = x + i * gap
        d.rounded_rectangle([fx - w * 0.28, y0, fx + w * 0.28, y1], radius=w * 0.28,
                            fill=(18, 19, 24))
        # Cap position differs per channel so it looks played, not reset.
        t = (0.30, 0.62, 0.44)[i % 3]
        cy = y1 - (y1 - y0) * t
        d.rounded_rectangle([fx - w, cy - w * 0.62, fx + w, cy + w * 0.62], radius=w * 0.3,
                            fill=lerp(BODY, BODY_HI, 0.35))
        d.rounded_rectangle([fx - w, cy - w * 0.10, fx + w, cy + w * 0.10], radius=w * 0.1,
                            fill=ACCENT)


def variant_jog(img):
    """One jog wheel, filling the frame. Strongest silhouette at thumbnail size."""
    d = ImageDraw.Draw(img)
    c = img.width / 2
    jog(d, c, c, img.width * 0.395)


def variant_booth(img):
    """Jog plus faders: reads as a booth, costs some clarity when small."""
    d = ImageDraw.Draw(img)
    w = img.width
    jog(d, w * 0.355, w * 0.5, w * 0.305)
    faders(d, w * 0.725, w * 0.24, w * 0.76, w * 0.040)


def variant_decks(img):
    """Two decks and a mixer between them, the actual layout of the block set."""
    d = ImageDraw.Draw(img)
    w = img.width
    jog(d, w * 0.225, w * 0.5, w * 0.185)
    jog(d, w * 0.775, w * 0.5, w * 0.185)
    faders(d, w * 0.445, w * 0.32, w * 0.68, w * 0.024, n=3)


VARIANTS = {"jog": variant_jog, "booth": variant_booth, "decks": variant_decks}


def render(name):
    big = Image.new("RGB", (S * SS, S * SS))
    backdrop(big)
    VARIANTS[name](big)

    # So the accent reads as emitting light rather than being painted on.
    big = bloom(big, S * SS * 0.012, 0.22)

    return big.resize((S, S), Image.LANCZOS)


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "jog"
    if which == "all":
        # Previews go beside the icon only long enough to look at; they are not resources.
        for name in VARIANTS:
            p = Path(__file__).resolve().parent.parent / f"_preview_{name}.png"
            render(name).save(p)
            print(f"wrote {p}")
    elif which == "sheet":
        # Judge the candidates at the sizes they are actually browsed at.
        sizes = [512, 96, 48]
        pad = 24
        sheet = Image.new("RGB", (sum(sizes) + pad * 4, 512 * len(VARIANTS) + pad * 4), (60, 60, 66))
        for row, name in enumerate(VARIANTS):
            img = render(name)
            x = pad
            for s in sizes:
                sheet.paste(img.resize((s, s), Image.LANCZOS), (x, pad + row * (512 + pad)))
                x += s + pad
        p = Path(__file__).resolve().parent.parent / "_icon_sheet.png"
        sheet.save(p)
        print(f"wrote {p}  (rows: {', '.join(VARIANTS)})")
    else:
        img = render(which)
        img.save(OUT)
        print(f"wrote {OUT} ({which})")
        # Thumbnail check: this is the size that actually decides whether it works.
        # Kept out of resources/ so it never ends up inside the jar.
        img.resize((48, 48), Image.LANCZOS).save(Path(__file__).resolve().parent.parent / "_thumb48.png")
