# DJ Booth

An interactive Pioneer-style DJ booth for Minecraft (NeoForge 1.21.1). Two CDJ decks and a mixer
that open a single booth GUI drawn over the real gear, stream real music, and can drive DMX stage
lights.

![logo](src/main/resources/djbooth_logo.png)

## Features

- **One booth GUI.** Right-click a deck or the mixer to open the whole rig at once, with every
  control on the artwork where it lives on real hardware.
- **Load any track.** Paste a link or type a song name (top YouTube hit). Recent tracks are one
  click away.
- **Deck controls.** Play/pause, smart cue (jump / right-click to set), loops (in / out / exit /
  reloop / halve / double), 4 hot cues, beat-jump, a pitch-bend jog wheel, click-to-seek on the
  deck screen, a scrolling waveform readout with elapsed/remaining time.
- **Tempo.** Tempo fader with a selectable range (±6 / ±10 / ±16 / WIDE) and a tap-tempo BPM counter.
- **Mixer.** Channel faders, crossfader, master. A real 3-band **isolator EQ**, a **colour sweep
  filter** and an **echo FX**, all processed live in Java. Switches for EQ/ISO curve and fader curve.
- **DMX (optional).** Sends lighting frames to MineDMX over UDP.

## Requirements

| Mod | Needed for | Required? |
| --- | --- | --- |
| NeoForge 21.1.x (MC 1.21.1) | the mod itself | yes |
| [WaterMedia](https://modrinth.com/mod/watermedia) + [WaterMedia Binaries](https://modrinth.com/mod/watermedia-binaries) | streaming audio | optional (no sound without it) |
| [MineDMX](https://modrinth.com/mod/minedmx) | DMX stage lights | optional |

The booth loads and works without the optional mods — it just stays silent / dark.

> WaterMedia is under a non-commercial license, so it is **not** bundled with this mod. Install it
> separately.

## Setup in-game

1. Place two **CDJ-3000** blocks with a **DJ Mixer** between (or beside) them. The booth finds the
   nearest deck+mixer group automatically.
2. Right-click any of them to open the booth.
3. Type a song or paste a link in a deck's box, press Enter, hit the green play button.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Run `./gradlew runClient` for a dev client. Textures are generated
from source art by the scripts in `tools/`.

## Credits

Built with [WaterMedia](https://github.com/WaterMediaTeam/watermedia) (audio) and MineDMX (lighting).
