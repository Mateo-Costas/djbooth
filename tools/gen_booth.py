"""Draws the booth GUI panel: assets/soundsystem_dj/textures/gui/booth.png, 1200x440.

The panel used to be three Pioneer press photos stitched together — two CDJ-3000s and a
DJM-900NXS2, model names and the Pioneer DJ wordmark legible on all three. That is a
manufacturer's product photography and a manufacturer's trademark, shipped inside a jar
published as All Rights Reserved. Not ours to hand out, so it is drawn instead.

The art is generated FROM BoothLayout.java rather than measured against it. Every hotspot
in that file is parsed here and painted where it lands, which inverts the old problem: the
photo dictated the numbers and the numbers drifted whenever the framing changed, whereas
now the numbers dictate the paint and the two cannot disagree. Move a control in the
layout, re-run this, and the panel follows.

Most controls draw themselves at runtime (PanelKnob paints its own body and pointer,
PanelFader its own cap), so those only need a well to sit in. PanelButton is the exception:
it is an invisible hotspot that shows nothing until lit or hovered, so every button needs a
visible cap here or the deck looks like a blank slab.

Usage: python tools/gen_booth.py
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LAYOUT = ROOT / "common/src/main/java/com/osgworld/djbooth/client/screen/BoothLayout.java"
OUT = ROOT / "common/src/main/resources/assets/soundsystem_dj/textures/gui/booth.png"

W, H = 1200, 440
SS = 3  # supersample: draw big, shrink down, get antialiasing for free

BG = (9, 9, 12)
CHASSIS = (41, 43, 50)
CHASSIS_HI = (78, 82, 93)
CHASSIS_LO = (20, 21, 26)
FACE = (34, 36, 43)
STRIP = (22, 24, 29)
RECESS = (15, 16, 20)
CAP = (68, 72, 82)
CAP_HI = (104, 110, 123)
INK = (138, 143, 154)
INK_DIM = (92, 96, 105)
ACCENT = (37, 224, 192)
CUE = (255, 86, 68)

FONT_PATHS = ["C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/segoeuib.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]


def font(size):
    for p in FONT_PATHS:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# --- Layout ------------------------------------------------------------------------

def read_layout():
    """Pull every `Rect NAME = new Rect(x, y, w, h)` out of BoothLayout.java."""
    src = LAYOUT.read_text(encoding="utf-8")
    pat = r"Rect\s+(\w+)\s*=\s*new Rect\(\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f,\s*([\d.]+)f\s*\)"
    rects = {m[0]: tuple(float(v) for v in m[1:]) for m in re.findall(pat, src)}
    missing = [n for n in ("REGION_DECK_A", "REGION_MIXER", "REGION_DECK_B") if n not in rects]
    if missing:
        raise SystemExit(f"BoothLayout.java parsed but {missing} not found — has the file moved?")
    return rects


L = read_layout()


def region_box(name):
    x, y, w, h = L[name]
    return (x * W * SS, y * H * SS, w * W * SS, h * H * SS)


def px(region, ctrl):
    """Same mapping BoothScreen.px() does, in supersampled pixels."""
    rx, ry, rw, rh = region_box(region)
    cx, cy, cw, ch = L[ctrl]
    return (rx + cx * rw, ry + cy * rh, cw * rw, ch * rh)


# --- Primitives --------------------------------------------------------------------

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rr(d, box, radius, fill):
    x, y, w, h = box
    d.rounded_rectangle([x, y, x + w, y + h], radius=radius, fill=fill)


def raised(d, box, radius, base=CAP, hi=CAP_HI, lip=None):
    """A cap sitting proud of the panel: lit along the top, shadowed underneath."""
    x, y, w, h = box
    lip = lip if lip is not None else max(1.0, h * 0.14)
    rr(d, (x, y - lip * 0.5, w, h + lip), radius, CHASSIS_LO)   # shadow it drops
    rr(d, (x, y - lip * 0.5, w, h), radius, hi)                 # lit top edge
    rr(d, (x, y, w, h - lip * 0.5), radius, base)               # face


def tinted_cap(d, box, radius, colour):
    """Transport buttons are coloured on every deck ever made, and PanelButton shows nothing
    until the deck is actually playing — so the colour has to be in the art."""
    raised(d, box, radius, base=lerp(colour, (0, 0, 0), 0.45), hi=lerp(colour, (255, 255, 255), 0.12))


def sunken(d, box, radius, fill=RECESS, rim=None):
    """A well cut into the panel: dark inside, with the far lip catching light."""
    x, y, w, h = box
    rim = rim if rim is not None else max(1.0, min(w, h) * 0.10)
    rr(d, (x - rim, y - rim, w + 2 * rim, h + 2 * rim), radius + rim, lerp(CHASSIS, CHASSIS_HI, 0.35))
    rr(d, (x - rim, y - rim, w + 2 * rim, h + 2 * rim * 0.6), radius + rim, CHASSIS_LO)
    rr(d, (x, y, w, h), radius, fill)


def well(d, box, pad=0.34):
    """Circular recess under a rotary control, sized off the control's own box."""
    x, y, w, h = box
    r = min(w, h) * (0.5 + pad)
    cx, cy = x + w / 2, y + h / 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=lerp(CHASSIS, CHASSIS_HI, 0.55))
    d.ellipse([cx - r, cy - r * 0.90, cx + r, cy + r], fill=CHASSIS_LO)
    d.ellipse([cx - r * 0.86, cy - r * 0.86, cx + r * 0.86, cy + r * 0.86], fill=(26, 28, 34))


def silk(d, text, x, y, size, color=INK, anchor="lt", track=0.14):
    """Silkscreen lettering: small, tracked out, the way panel legends are printed."""
    f = font(size)
    widths = [d.textlength(c, font=f) for c in text]
    gap = size * track
    total = sum(widths) + gap * (len(text) - 1)
    if "m" in anchor:
        x -= total / 2
    elif "r" in anchor:
        x -= total
    for c, cw in zip(text, widths):
        d.text((x, y), c, font=f, fill=color)
        x += cw + gap
    return total


def vsilk(img, text, cx, cy, size, color=INK_DIM, track=0.5):
    """The same lettering turned on its side, for the tall gap down the middle of the mixer."""
    pad = size * 2
    scratch = Image.new("RGBA", (int(size * len(text) * (1 + track) + pad), int(size * 2 + pad)),
                        (0, 0, 0, 0))
    sd = ImageDraw.Draw(scratch)
    silk(sd, text, pad / 2, pad / 2, size, color, track=track)
    scratch = scratch.rotate(90, expand=True, resample=Image.BICUBIC)
    img.paste(scratch, (int(cx - scratch.width / 2), int(cy - scratch.height / 2)), scratch)


def scale_ticks(d, box, n, side="right", color=INK_DIM, span=1.0):
    """The printed travel scale beside a fader."""
    x, y, w, h = box
    for i in range(n + 1):
        ty = y + h * i / n
        long = (i == 0 or i == n or i == n // 2)
        ln = w * span * (1.1 if long else 0.6)
        tx = x + w + w * 0.30 if side == "right" else x - w * 0.30 - ln
        d.rectangle([tx, ty - SS * 0.5, tx + ln, ty + SS * 0.5], fill=color)


def bloom(img, radius, strength):
    """Blur a copy and screen it back, so the accent lighting bleeds instead of sitting flat."""
    blurred = img.filter(ImageFilter.GaussianBlur(radius))
    return ImageChops.screen(img, blurred.point(lambda v: int(v * strength)))


# --- Panel pieces ------------------------------------------------------------------

def backdrop(d):
    d.rectangle([0, 0, W * SS, H * SS], fill=BG)
    # Faint brushed grain across the whole desk, so the flats are not dead flat.
    for i in range(0, W * SS, SS * 3):
        v = 3 + int(2 * math.sin(i * 0.011))
        d.rectangle([i, 0, i + SS, H * SS], fill=(BG[0] + v, BG[1] + v, BG[2] + v + 1))


def chassis(d, name, label):
    """The device body: a plate with a lit top edge, and a slightly darker working face."""
    x, y, w, h = region_box(name)
    pad = SS * 4
    rr(d, (x - pad, y - pad, w + 2 * pad, h + 2 * pad), SS * 8, CHASSIS_LO)
    rr(d, (x - pad, y - pad, w + 2 * pad, h + 2 * pad - SS * 3), SS * 8, CHASSIS_HI)
    rr(d, (x - pad, y - pad + SS * 2, w + 2 * pad, h + 2 * pad - SS * 5), SS * 8, CHASSIS)
    rr(d, (x, y, w, h), SS * 6, FACE)
    # Top-lit gradient across the working face: without it the flats read as one dead slab.
    for i in range(int(h)):
        t = i / h
        d.rectangle([x, y + i, x + w, y + i + 1], fill=lerp(lerp(FACE, CHASSIS_HI, 0.14), FACE, t ** 0.55))
    silk(d, label, x + w * 0.5, y + h * 0.962, SS * 9, INK_DIM, anchor="m")


def jog(d, box):
    """The jog wheel: brushed rim, dimpled platter, centre display. The PanelJog widget
    draws the moving marker on top, so this is the parked hardware underneath."""
    x, y, w, h = box
    cx, cy = x + w / 2, y + h / 2
    r = min(w, h) / 2

    # Machined rim, brightness sweeping around so it reads as metal, not a donut.
    steps = 720
    span = 360 / steps
    for i in range(steps):
        a = i * span
        t = (math.cos(math.radians(2 * (a - 35))) + 1) / 2
        d.pieslice([cx - r * 1.06, cy - r * 1.06, cx + r * 1.06, cy + r * 1.06],
                   a, a + span * 1.8, fill=lerp((38, 40, 47), (132, 138, 152), t ** 1.6))
    d.ellipse([cx - r * 1.01, cy - r * 1.01, cx + r * 1.01, cy + r * 1.01], fill=(18, 19, 24))

    # Platter face, domed: bright where the rim light falls, falling away to the centre.
    for i in range(28):
        t = i / 28
        rr_ = r * (1 - t * 0.98)
        d.ellipse([cx - rr_, cy - rr_, cx + rr_, cy + rr_], fill=lerp((52, 55, 64), (27, 28, 35), t ** 0.6))

    # Dimples: the detail that says jog wheel and not vinyl.
    rd, dot = r * 0.78, r * 0.045
    for i in range(24):
        a = math.radians(i * 15)
        px_, py_ = cx + rd * math.cos(a), cy + rd * math.sin(a)
        d.ellipse([px_ - dot, py_ - dot, px_ + dot, py_ + dot], fill=(19, 20, 25))

    # Centre display well.
    rc = r * 0.40
    d.ellipse([cx - rc, cy - rc, cx + rc, cy + rc], fill=(13, 14, 18))
    d.ellipse([cx - rc * 0.9, cy - rc * 0.9, cx + rc * 0.9, cy + rc * 0.9], fill=(20, 22, 28))


def deck(d, region, label):
    """One deck: screen window, transport caps, jog, tempo fader."""
    chassis(d, region, label)
    rx, ry, rw, rh = region_box(region)

    # Screen window: the URL box and the live readout share one recessed pane, because on
    # the hardware they are one display and a seam between them would look like a fault.
    ub = px(region, "DECK_URLBAR")
    sc = px(region, "DECK_SCREEN")
    top = min(ub[1], sc[1])
    bot = max(ub[1] + ub[3], sc[1] + sc[3])
    left = min(ub[0], sc[0])
    right = max(ub[0] + ub[2], sc[0] + sc[2])
    m = SS * 4
    sunken(d, (left - m, top - m, right - left + 2 * m, bot - top + 2 * m), SS * 3, fill=(10, 11, 14))

    # Button banks. Everything not otherwise handled is a cap, because PanelButton draws
    # nothing until it is lit.
    for name in DECK_BUTTONS:
        if name in TRANSPORT_TINT:
            tinted_cap(d, px(region, name), SS * 3, TRANSPORT_TINT[name])
        else:
            raised(d, px(region, name), SS * 2)

    # Jog and its surround.
    jog(d, px(region, "DECK_JOG"))

    # Tempo fader: slot and printed scale. No legend — the fader sits hard against the right
    # edge of the chassis with the jog rim on its other side, and any text there either
    # clipped off the panel or landed on the wheel.
    t = px(region, "DECK_TEMPO")
    sunken(d, (t[0] + t[2] * 0.32, t[1] - SS * 2, t[2] * 0.36, t[3] + SS * 4), SS * 2)
    scale_ticks(d, t, 10, "right")


def channel_strip(d, region, x_centre, label):
    """The vertical plate a mixer channel's controls sit on."""
    rx, ry, rw, rh = region_box(region)
    w = rw * 0.115
    x = rx + rw * x_centre - w / 2
    rr(d, (x, ry + rh * 0.115, w, rh * 0.75), SS * 4, STRIP)
    silk(d, label, x + w / 2, ry + rh * 0.070, SS * 8, INK, anchor="m")


def mixer(img, region):
    d = ImageDraw.Draw(img)
    chassis(d, region, "MIXER")
    rx, ry, rw, rh = region_box(region)

    # Channel plates, centred on the columns the layout already puts the knobs in.
    channel_strip(d, region, L["MIX_HI_A"][0] + L["MIX_HI_A"][2] / 2, "CH 1")
    channel_strip(d, region, L["MIX_HI_B"][0] + L["MIX_HI_B"][2] / 2, "CH 2")

    # The hardware this is modelled on has four channels; this mixer has two, which leaves a
    # tall empty column between channel 2 and the master. A real mixer would print its name
    # there, so this one prints its own.
    gap_l, gap_r, gap_t, gap_b = 0.450, 0.660, 0.100, 0.860
    rr(d, (rx + gap_l * rw, ry + gap_t * rh, (gap_r - gap_l) * rw, (gap_b - gap_t) * rh),
       SS * 4, STRIP)
    vsilk(img, "SOUNDSYSTEM", rx + (gap_l + gap_r) / 2 * rw, ry + (gap_t + gap_b) / 2 * rh,
          SS * 11, INK_DIM)

    # FX panel down the right-hand edge, boxed so it reads as its own section.
    fx_top = min(L[n][1] for n in FX_BOXED)
    fx_bot = max(L[n][1] + L[n][3] for n in FX_BOXED)
    fx_l = min(L[n][0] for n in FX_BOXED)
    fx_r = max(L[n][0] + L[n][2] for n in FX_BOXED)
    pad = SS * 5
    rr(d, (rx + fx_l * rw - pad, ry + fx_top * rh - pad,
           (fx_r - fx_l) * rw + 2 * pad, (fx_bot - fx_top) * rh + 2 * pad), SS * 4, STRIP)
    silk(d, "BEAT FX", rx + (fx_l + fx_r) / 2 * rw, ry + fx_top * rh - SS * 15, SS * 7,
         INK, anchor="m")

    for name in MIXER_KNOBS:
        well(d, px(region, name))
    for name in MIXER_BUTTONS:
        raised(d, px(region, name), SS * 2)
    for name in ("MIX_METER_A", "MIX_METER_B"):
        b = px(region, name)
        sunken(d, (b[0] - SS, b[1] - SS, b[2] + 2 * SS, b[3] + 2 * SS), SS, fill=(9, 10, 13))

    # Channel faders and master: slot plus printed scale. The master's scale goes on its
    # left, because the FX panel is immediately to its right and the ticks landed inside it.
    for name, side, span in (("MIX_FADER_A", "right", 1.0), ("MIX_FADER_B", "right", 1.0),
                             ("MIX_MASTER", "left", 0.6)):
        b = px(region, name)
        sunken(d, (b[0] + b[2] * 0.33, b[1] - SS * 2, b[2] * 0.34, b[3] + SS * 4), SS * 2)
        scale_ticks(d, b, 8, side, span=span)
    mb = px(region, "MIX_MASTER")
    silk(d, "MASTER", mb[0] + mb[2] / 2, mb[1] - SS * 14, SS * 7, INK, anchor="m")

    # Crossfader: horizontal slot with a centre index mark.
    xf = px(region, "MIX_XFADER")
    sunken(d, (xf[0] - SS * 2, xf[1] + xf[3] * 0.34, xf[2] + SS * 4, xf[3] * 0.32), SS * 2)
    d.rectangle([xf[0] + xf[2] / 2 - SS * 0.5, xf[1] - SS * 3,
                 xf[0] + xf[2] / 2 + SS * 0.5, xf[1] - SS], fill=ACCENT)

    # Grid sections get a plate behind them so the buttons read as one bank.
    for name in ("MIX_COLOR_MODES", "FX_TYPES", "FX_BEATS", "FX_FREQ", "FX_CHANNEL"):
        b = px(region, name)
        p = SS * 2
        rr(d, (b[0] - p, b[1] - p, b[2] + 2 * p, b[3] + 2 * p), SS * 2, RECESS)
    silk(d, "COLOR FX", rx + (L["MIX_COLOR_MODES"][0] + L["MIX_COLOR_MODES"][2] / 2) * rw,
         ry + L["MIX_COLOR_MODES"][1] * rh - SS * 14, SS * 6, INK, anchor="m")


# Classification. Names are unambiguous here, and keeping the lists explicit means a new
# control shows up as an obvious omission rather than being silently mis-drawn by a
# pattern match that happened to catch it.
DECK_BUTTONS = ["DECK_PLAY", "DECK_CUE", "DECK_LOOP", "DECK_DIRECTION", "DECK_SLIP",
                "DECK_QUANTIZE", "DECK_JOGMODE", "DECK_TEMPO_RESET", "DECK_MASTER_TEMPO",
                "DECK_BEAT_SYNC", "DECK_KEY_SYNC", "DECK_TRACK_START", "DECK_SEARCH_BACK",
                "DECK_SEARCH_FWD", "DECK_CALL_PREV", "DECK_CALL_NEXT", "DECK_MEM_DELETE",
                "DECK_MEMORY"]
MIXER_KNOBS = ["MIX_HI_A", "MIX_MID_A", "MIX_LOW_A", "MIX_FILTER_A", "MIX_GAIN_A",
               "MIX_HI_B", "MIX_MID_B", "MIX_LOW_B", "MIX_FILTER_B", "MIX_GAIN_B",
               "MIX_ECHO_A", "MIX_ECHO_B", "MIX_COLOR_PARAM", "MIX_BALANCE", "MIX_BOOTH",
               "FX_DEPTH"]
MIXER_BUTTONS = ["MIX_XF_ASSIGN_A", "MIX_XF_ASSIGN_B", "MIX_ISOLATOR", "MIX_FADERCURVE",
                 "MIX_XFCURVE", "MIX_CUE_A", "MIX_CUE_B", "FX_TAP", "FX_ONOFF"]
FX_BOXED = ["FX_BEATS", "FX_TAP", "FX_FREQ", "FX_TYPES", "FX_CHANNEL", "FX_DEPTH", "FX_ONOFF"]
TRANSPORT_TINT = {"DECK_PLAY": (46, 200, 96), "DECK_CUE": (240, 150, 40)}


def unclassified():
    """Anything in the layout this script does not know how to paint."""
    known = set(DECK_BUTTONS) | set(MIXER_KNOBS) | set(MIXER_BUTTONS) | {
        "REGION_DECK_A", "REGION_MIXER", "REGION_DECK_B", "DECK_JOG", "DECK_TEMPO",
        "DECK_SCREEN", "DECK_URLBAR", "MIX_FADER_A", "MIX_FADER_B", "MIX_MASTER",
        "MIX_XFADER", "MIX_METER_A", "MIX_METER_B", "MIX_COLOR_MODES",
        "FX_BEATS", "FX_FREQ", "FX_TYPES", "FX_CHANNEL"}
    return sorted(set(L) - known)


def main():
    stray = unclassified()
    if stray:
        print(f"warning: no artwork drawn for {stray}")

    img = Image.new("RGB", (W * SS, H * SS))
    d = ImageDraw.Draw(img)
    backdrop(d)
    deck(d, "REGION_DECK_A", "DECK A")
    deck(d, "REGION_DECK_B", "DECK B")
    mixer(img, "REGION_MIXER")

    img = bloom(img, SS * 3, 0.16)
    img.resize((W, H), Image.LANCZOS).save(OUT)
    print(f"wrote {OUT} ({W}x{H})")


if __name__ == "__main__":
    main()
