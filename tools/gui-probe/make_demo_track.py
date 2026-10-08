"""Synthesise a short four-on-the-floor loop for the gallery screenshots.

Our own work, so there is nothing to license: a kick, an off-beat hat, a bass line and a pad, 128 BPM,
two minutes, 22.05 kHz stereo. It exists only so a deck has a real track to load in a screenshot.
"""
import math
import random
import struct
import wave
from pathlib import Path

out_dir = Path(__file__).resolve().parent / "audio"  # gitignored, see .gitignore
out_dir.mkdir(parents=True, exist_ok=True)

SR = 22050
BPM = 128
SECONDS = 120
beat = 60.0 / BPM
n = SR * SECONDS
random.seed(7)
buf = [0.0] * n


def add(start_s, samples, gain=1.0):
    i0 = int(start_s * SR)
    for k, v in enumerate(samples):
        if i0 + k >= n:
            break
        buf[i0 + k] += v * gain


def kick():
    length = int(0.28 * SR)
    out = []
    phase = 0.0
    for k in range(length):
        t = k / SR
        f = 45 + 110 * math.exp(-t * 28)
        phase += 2 * math.pi * f / SR
        out.append(math.sin(phase) * math.exp(-t * 11))
    return out


def hat():
    length = int(0.06 * SR)
    prev = 0.0
    out = []
    for k in range(length):
        t = k / SR
        x = random.uniform(-1, 1)
        hp = x - prev  # crude high-pass
        prev = x
        out.append(hp * math.exp(-t * 70) * 0.5)
    return out


def bass(freq):
    length = int(beat * 0.45 * SR)
    return [math.sin(2 * math.pi * freq * k / SR) * math.exp(-k / SR * 4.5) * 0.55 for k in range(length)]


K, H = kick(), hat()
notes = [55.0, 55.0, 65.41, 49.0]  # A1 A1 C2 G1, one per bar
total_beats = int(SECONDS / beat)
for b in range(total_beats):
    t = b * beat
    add(t, K, 0.9)
    add(t + beat / 2, H, 0.35)
    if b % 4 in (0, 2):
        bar = (b // 4) % 4
        add(t + beat / 2, bass(notes[bar]), 0.5)
    # a soft pad that slowly opens and closes, so the wave has some shape
for i in range(n):
    t = i / SR
    env = 0.5 + 0.5 * math.sin(2 * math.pi * t / 16.0 - 1.2)
    buf[i] += 0.06 * env * (math.sin(2 * math.pi * 220 * t) + math.sin(2 * math.pi * 277.18 * t) +
                            math.sin(2 * math.pi * 329.63 * t))

peak = max(abs(v) for v in buf) or 1.0
scale = 0.85 / peak
path = out_dir / "demo-set.wav"
with wave.open(str(path), "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    frames = bytearray()
    for v in buf:
        s = int(max(-1.0, min(1.0, v * scale)) * 32767)
        frames += struct.pack("<hh", s, s)
    w.writeframes(bytes(frames))
print(path, path.stat().st_size, "bytes,", SECONDS, "s")
