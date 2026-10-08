> **Beta.** Feature-complete and tested, but young. Expect rough edges, and report them on [GitHub](https://github.com/Mateo-Costas/djbooth/issues).

# Soundsystem DJ

**A playable club-style DJ booth for Minecraft.** Two decks, one mixer, one screen. Paste a link or type a song name, then mix it with real EQ, filters and effects, in your own world or with friends.

![The booth screen](https://cdn.modrinth.com/data/KNXZi4yx/images/e7a1d1e34b400e5dc65cfbf866a6bf2968759e2b.png)

Runs on **NeoForge** and **Fabric** for **Minecraft 1.21.1**. Both blocks are craftable in survival (iron, redstone and a note block), and a deck gives off a little light while it is set to play.

## Highlights

- **One screen for the whole booth.** Right-click the mixer or a deck and every control sits where it sits on a real booth, and does what it does there.
- **Real DSP, not presets.** A three-band isolator EQ, six Sound Color FX and fourteen Beat FX, all processing the audio live.
- **Proper deck features.** Hot cues, loops, beat jump, slip, quantize, master tempo, vinyl and CDJ jog modes, a scrolling waveform with the whole track below it.
- **Headphone cue that works in a shared world.** Cue a channel at the booth and only you hear it; everyone else keeps hearing the master.
- **Nothing bundled.** The mod ships no music. Tracks stream from links on each player's own client.

## Quick start

1. Install the [requirements](#requirements) below. Without WaterMedia the booth works but is silent.
2. Craft two **DJ Deck** blocks and one **DJ Mixer**, and put the mixer between the decks.
3. Right-click any of them to open the booth.
4. Type a song name or paste a link into a deck's box and press **Enter**. The deck screen says LOADING while the track opens.
5. Press **PLAY**. Load the other deck, bring it in with the crossfader, and mix.

![The booth in the world](https://cdn.modrinth.com/data/KNXZi4yx/images/499d1a8263ee4b79ed9a863e76a879cac693ac27.png)

## Where the music comes from

**The mod ships no music.** There are no audio files inside it and no built-in library, and songs are never installed on the server or the client. Every track is streamed from a link when you play it:

- **Paste a link** into a deck's box and press Enter, or **type a song name** and the mod searches YouTube and loads the first result.
- A direct link only plays if the server sends an audio or video content type, and does not throttle the player.
- Playback happens on **each player's own game client**, through [WaterMedia](https://modrinth.com/mod/watermedia) + WaterMedia Binaries. It plays whatever links WaterMedia can resolve: YouTube, direct links to audio or video files, and the other platforms WaterMedia supports.
- **The server never downloads or relays audio.** It only stores the link and the deck's playback state (playing, position, tempo) and syncs that to nearby players, so every client streams the track itself, and every client contacts the host of the link.
- The server command `/soundsystem track <url>` loads a link onto the nearest deck.

You are responsible for having the right to play whatever you load.

## The decks

Play/pause, cue (right-click sets it), loops (in / out / exit / reloop / half / double), four hot cues, beat jump, click-to-seek and a scrolling waveform.

- **Tempo**: a fader with selectable range (±6 / ±10 / ±16 / WIDE), TEMPO RESET, and tap tempo. The fader runs the right way round: pushing it up slows the track down, as on club gear.
- **MASTER TEMPO**: hold the key while the tempo moves.
- **SLIP**: loop or scratch on top while the track carries on underneath; let go and you land where it should be.
- **QUANTIZE**: cues and loops snap to the beat.
- **DIRECTION**: FWD / REV / SLIP REV.
- **JOG MODE**: VINYL or CDJ. The jog scrubs both ways and bends the pitch while playing, and its marker turns with the track.
- **BEAT SYNC**, TRACK SEARCH, SEARCH, and saved cues via MEMORY / CUE·LOOP CALL / DELETE.

![A deck](https://cdn.modrinth.com/data/KNXZi4yx/images/1e4dd10f82189295fee8fd777f2c1f2cceb321b0.png)

## The mixer

- **Channel EQ**: real `-26 dB … +6 dB` band gains, with the EQ CURVE switch choosing a `-26 dB` cut or a full kill.
- **SOUND COLOR FX**: all six, SPACE, DUB ECHO, SWEEP, NOISE, CRUSH and FILTER, driven by the COLOR knob and scaled by PARAMETER. Centre is a real detent, bit-exact dry.
- **BEAT FX**: all fourteen, DELAY, ECHO, PING PONG, SPIRAL, REVERB, TRANS, FILTER, FLANGER, PHASER, PITCH, SLIP ROLL, ROLL, VINYL BRAKE and HELIX. Beat fractions 1/16 to 4, TAP tempo, FX FREQUENCY band selection, channel select and LEVEL/DEPTH. The channel fader sits ahead of the effects, so closing it leaves the echo ringing, as on the real thing.
- **Level meters** fed by the actual audio, read before the fader.
- TRIM per channel, BALANCE, BOOTH MONITOR, CUE, CROSS FADER ASSIGN (A / THRU / B), and three-position curve switches for both faders.

Everything is processed live: the EQ, filters and effects are real DSP running on the audio, not presets. The mixer was checked against the published manual of a club mixer, and every claim is written down with its test in the [reference document](https://github.com/Mateo-Costas/djbooth/blob/master/docs/design/djm-900nxs2-reference.md).

![The mixer](https://cdn.modrinth.com/data/KNXZi4yx/images/03785499c852b5990b05b70c01bdf11ecbf913ed.png)

## Working the controls

Every knob and fader takes the **scroll wheel** as well as dragging. Hold **shift** for fine steps, **double-click** to reset, and hover to see the live value in hardware units (`+3.0 dB`, `LPF 60%`).

## Headphone cue, in a shared world

The booth is the blocks right around the mixer. Stand there and you hear the BOOTH MONITOR feed; hit CUE on a channel and it previews for you alone, while everyone on the floor carries on hearing the master.

## New in 1.0.2

- **Fixed decks that could stay silent for good.** The deck started its player while the track was still opening, and WaterMedia then never started its audio decoder. Whether a link played depended on how fast it opened. The screen now says LOADING while it opens and COULD NOT LOAD THIS LINK if it gives up.
- **Fixed the EQ, trim and effects being applied more than once**, which showed up as harsh highs, a trim that jumped to loud, and buzzing on the low end.
- **The mixer follows the manual more closely**: channel fader ahead of BEAT FX, real crossfader curves, SWEEP and DUB ECHO as documented, longer delay times.
- **A clearer booth screen**: a proper title, switches that fit their labels, hot cue pads that show whether a cue is set, and a deck screen that says what is happening.
- **Hardened for servers**: invalid values from a modified client are ignored, and track links must be `http` or `https`.
- Both blocks drop themselves when broken, are mined with a pickaxe, and can be crafted in survival.

## Good to know

- Every player streams the track themselves, so how it sounds depends on their connection and on the host of the link.
- The stream can only seek, so scratching and reverse are approximations: they sound like a tape rewind rather than a record.
- The server cannot tell when a track ends. The deck keeps showing PLAY (and keeps glowing) until you pause it.

## Requirements

| | |
|---|---|
| Minecraft | **1.21.1** |
| Loader | **NeoForge** 21.1.x or **Fabric** |
| [Architectury API](https://modrinth.com/mod/architectury-api) | required |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required on Fabric |
| [WaterMedia](https://modrinth.com/mod/watermedia) + [WaterMedia Binaries](https://modrinth.com/mod/watermedia-binaries) | required for any sound. The booth loads without them, silent. WaterMedia is not bundled; install it separately. |
| [MineDMX](https://modrinth.com/mod/minedmx) | optional. Only used by a demo light sweep (`/soundsystem dmxtest`) for now; lights that follow the music are not built yet. |

## Automatic tempo and key (optional)

The booth can look a track's BPM and musical key up by name when it loads, which is what makes **QUANTIZE**, **BEAT SYNC** and **KEY SYNC** work without tapping a tempo in first. The deck shows the key in both notations, Camelot included.

It's **off by default** and needs a free [GetSongBPM](https://getsongbpm.com/api) API key. No key ships with the mod: put your own in `config/soundsystem_dj.properties`, written as a commented template on first run. With a key set, the song name you typed is sent to GetSongBPM to look it up. With none configured the lookup sends nothing and everything falls back to the TAP button.

Tempo and key data by [GetSongBPM.com](https://getsongbpm.com).

## AI usage disclosure

This mod was made with AI assistance, and that is most of it:

- **Code**: written by Claude (Anthropic) through Claude Code, directed and tested by the author. That includes the audio DSP, networking, GUI and build setup.
- **Text**: this description, the in-game names, the English and Spanish translations and the changelogs were written with AI assistance.
- **Art**: the GUI panel, the four block textures and the icon are not hand-painted and not made by an image model. They are drawn by Python scripts (written with AI assistance) from geometric primitives. The block models are hand-written JSON.

The source, including those scripts, is on [GitHub](https://github.com/Mateo-Costas/djbooth).
