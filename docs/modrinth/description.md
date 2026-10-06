> **Beta.** Feature-complete and tested, but young. Expect rough edges; feedback welcome.

# Soundsystem DJ

A playable club-style DJ booth for Minecraft. Place two **DJ Deck** blocks either side of a **DJ Mixer**, right-click, and you get one booth GUI — with the controls where they live on a real booth, doing what they do on a real booth.

Runs on **NeoForge** and **Fabric** for 1.21.1. Both blocks are craftable in survival (iron, redstone and a note block), and a deck gives off a little light while it plays.

## Load a track

Type a song name or paste a link and hit Enter. Recent tracks are one click away.

## Where the music comes from

**The mod ships no music.** There are no audio files inside it and no built-in library. Every track is streamed from a link when you play it:

- **Paste a link** into a deck's box and press Enter, or **type a song name** and the mod searches YouTube and loads the first result.
- Playback happens on **each player's own game client**, through [WaterMedia](https://modrinth.com/mod/watermedia) + WaterMedia Binaries. It plays whatever links WaterMedia can resolve: YouTube, direct links to audio or video files, and the other platforms WaterMedia supports. Without WaterMedia the booth works but is silent.
- **The server never downloads or relays audio.** It only stores the link and the deck's playback state (playing, position, tempo) and syncs that to nearby players, so every client streams the track itself, and every client contacts the host of the link.
- There is also a server command, `/soundsystem track <url>`, that loads a link onto the nearest deck.

You are responsible for having the right to play whatever you load.

## The decks

Play/pause, cue (right-click sets it), loops (in / out / exit / reloop / half / double), four hot cues, beat jump, click-to-seek and a scrolling waveform.

- **Tempo** — fader with selectable range (±6 / ±10 / ±16 / WIDE), TEMPO RESET, and tap tempo. The fader runs the right way round: pushing it up slows the track down, as on club gear.
- **MASTER TEMPO** — hold the key while the tempo moves.
- **SLIP** — loop or scratch on top while the track carries on underneath; let go and you land where it should be.
- **QUANTIZE** — cues and loops snap to the beat.
- **DIRECTION** — FWD / REV / SLIP REV.
- **JOG MODE** — VINYL or CDJ. The jog scrubs both ways and bends the pitch while playing.
- **BEAT SYNC**, TRACK SEARCH, SEARCH, and saved cues via MEMORY / CUE·LOOP CALL / DELETE.

## The mixer

- **Channel EQ** — real `-26 dB … +6 dB` band gains, with the EQ CURVE switch choosing a `-26 dB` cut or a full kill.
- **SOUND COLOR FX** — all six: SPACE, DUB ECHO, SWEEP, NOISE, CRUSH and FILTER, driven by the COLOR knob and scaled by PARAMETER. Centre is a real detent, bit-exact dry.
- **BEAT FX** — all fourteen: DELAY, ECHO, PING PONG, SPIRAL, REVERB, TRANS, FILTER, FLANGER, PHASER, PITCH, SLIP ROLL, ROLL, VINYL BRAKE and HELIX. Beat fractions 1/16 to 4, TAP tempo, FX FREQUENCY band selection, channel select and LEVEL/DEPTH.
- **Level meters** fed by the actual audio, not by fader positions.
- TRIM per channel, BALANCE, BOOTH MONITOR, CUE, CROSS FADER ASSIGN (A / THRU / B), and three-position curve switches for both faders.

Everything is processed live: the EQ, filters and effects are real DSP running on the audio, not presets.

## Working the controls

Every knob and fader takes the **scroll wheel** as well as dragging. Hold **shift** for fine steps, **double-click** to reset, and hover to see the live value in hardware units (`+3.0 dB`, `LPF 60%`).

## Headphone cue, in a shared world

The booth is the blocks right around the mixer. Stand there and you hear the BOOTH MONITOR feed; hit CUE on a channel and it previews for you alone, while everyone on the floor carries on hearing the master.

## Requirements

- Minecraft **1.21.1**, NeoForge 21.1.x or Fabric
- [Architectury API](https://modrinth.com/mod/architectury-api) — required
- [Fabric API](https://modrinth.com/mod/fabric-api) — required on Fabric
- [WaterMedia](https://modrinth.com/mod/watermedia) + [WaterMedia Binaries](https://modrinth.com/mod/watermedia-binaries) — optional, needed for any sound
- [MineDMX](https://modrinth.com/mod/minedmx) — optional, drives stage lights

The booth loads and works without the optional mods (silent, no lights). WaterMedia is not bundled; install it separately.

## Automatic tempo and key (optional)

The booth can look a track's BPM and musical key up by name when it loads, which is what makes **QUANTIZE**, **BEAT SYNC** and **KEY SYNC** work without tapping a tempo in first. The deck shows the key in both notations, Camelot included.

It's **off by default** and needs a free [GetSongBPM](https://getsongbpm.com/api) API key. No key ships with the mod — put your own in `config/soundsystem_dj.properties`, written as a commented template on first run. With a key set, the song name you typed is sent to GetSongBPM to look it up. With none configured the lookup sends nothing and everything falls back to the TAP button.

Tempo and key data by [GetSongBPM.com](https://getsongbpm.com).

## AI usage disclosure

This mod was made with AI assistance, and that is most of it:

- **Code** — written by Claude (Anthropic) through Claude Code, directed and tested by the author. That includes the audio DSP, networking, GUI and build setup.
- **Text** — this description, the in-game names, the English and Spanish translations and the changelogs were written with AI assistance.
- **Art** — the GUI panel, the four block textures and the icon are not hand-painted and not made by an image model. They are drawn by Python scripts (written with AI assistance) from geometric primitives. The block models are hand-written JSON.

The source, including those scripts, is on [GitHub](https://github.com/Mateo-Costas/djbooth).

