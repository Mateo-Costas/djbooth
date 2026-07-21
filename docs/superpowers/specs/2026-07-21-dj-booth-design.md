# DJ Booth — Design Spec

- **Date:** 2026-07-21
- **Mod id:** `djbooth`
- **Display name:** DJ Booth
- **Root package:** `com.osgworld.djbooth`
- **Target:** Minecraft 1.21.1, NeoForge 21.1.235 (server + client)
- **Soft dependencies:** WaterMedia/WaterFrames (audio), MineDMX 1.5.1 (lights)
- **Coexists with:** Create 6.0.10, MineDMX 1.5.1, WaterMedia
- **Goal:** Interactive Pioneer-style DJ booth (2 CDJ decks + mixer) that plays music, mixes volume between two tracks, and drives DMX lights. Surprise gift; polished enough to publish if good.

---

## 1. Reality constraints (read first)

Minecraft has **no native scrubbable audio API**. Everything below is shaped by that.

- **Audio is per-client.** The server cannot emit one shared audio stream. Each connected client renders its own audio. "Sync" therefore means: every client independently simulates the *same deck state* and drives its own player to match.
- **Real audio path = WaterMedia** (VLC-backed, client-side). Only path offering seek, tempo (rate), and position readback → required for anything that feels like a deck. Costs: each client needs VLC available to WaterMedia; seek has latency (~tens of ms granularity); cross-client sync is approximate, not sample-accurate.
- **Resource-pack sound events** (`SimpleSoundInstance`) are used only for short UI SFX (button clicks, cue beep). They cannot seek or scrub, so they are not the deck engine.
- **True DVS/vinyl scratch is not achievable.** Best result = rapid seek + rate change = "scratchy," not turntablism. v1 jog wheel = nudge (tempo bend) + coarse scrub, explicitly not sample-accurate scratch.
- **Automatic beat detection is out of scope.** No cheap FFT from WaterMedia. Beat/light sync uses manual **BPM tap + phase**, then a timer drives lights.
- **MineDMX API is unverified.** The 1.5.1 jar lives on the user's server and has not been inspected. The DMX layer is designed against an abstract interface; the concrete mapping to MineDMX (`com.romanbrady.minedmx`, `ModBlocks`/`ModBlockEntities`/`ModDataComponents`) is locked in a later phase once the jar is read. This is the one open dependency; it blocks only the DMX concrete impl, nothing else.

---

## 2. Scope

### In v1
- Two `CdjBlock` decks (place two copies of one block) + one `MixerBlock`.
- Decks: load a track by URL, play/pause, cue, loop, tempo slider, jog nudge + coarse scrub, position readout.
- Mixer: 2 channel faders, crossfader, master, transport/FX buttons; hub that binds two decks.
- Volume mixing between the two decks (crossfader + channel faders + master).
- FX v1: 1–2 simple effects (e.g. low/high filter if WaterMedia EQ is exposed; otherwise a volume/gate FX as fallback).
- DMX: mixer control → MineDMX channel map (manual assignment in GUI). BPM-tap light pulse.
- Multi-block linking (mixer auto-binds nearest 2 decks; GUI relink fallback).
- Graceful degradation: mod loads and controls still work with WaterMedia and/or MineDMX absent (deck silent / lights no-op).

### Out of v1 (future)
- Sample-accurate scratch / DVS.
- Automatic beat/onset detection.
- Video playback on deck faces.
- Frame-perfect cross-client audio sync.
- More than 2 decks; effects racks; recording.

---

## 3. Architecture overview

```
        MixerBlockEntity  (HUB — authoritative for mix + DMX)
        ├─ channelFaderA/B, crossfader, master  (floats)
        ├─ button states (play/cue/loop/fx per deck)
        ├─ dmxMap: control -> (universe, channel)
        └─ bound deck refs: BlockPos deckA, BlockPos deckB
             │
   ┌─────────┴─────────┐
CdjBlockEntity A     CdjBlockEntity B   (authoritative for deck state)
 ├─ trackUrl          (same fields)
 ├─ playState (STOP/PLAY/PAUSE/CUE)
 ├─ startEpochMs, offsetMs   (for position sim)
 ├─ rate (tempo), cuePoint, loopIn/loopOut
 └─ volume (derived; set by mixer)

Client per-deck:  AudioBackend player mirrors BE state (seek on drift)
Server:           holds truth in BEs, syncs via payloads + BE update tags
```

### Component boundaries
- **`CdjBlockEntity`** — owns one deck's transport state. Knows nothing about audio rendering or DMX. Serializes to NBT; syncs via update tag + payloads.
- **`MixerBlockEntity`** — owns mix values, button states, DMX map, and deck bindings. Computes per-deck gain. Emits DMX pushes. Does not render audio.
- **`AudioBackend` (interface)** — `load(url)`, `play/pause`, `seek(ms)`, `setRate`, `setVolume`, `getPositionMs()`. Impl `WaterMediaBackend` (soft dep). Client-side only, one player per deck per client.
- **`DmxBridge` (interface)** — `send(universe, channel, value0_255)`, `isAvailable()`. Impl `MineDmxBridge` (soft dep, reflective). Locked after jar inspection.
- **`DjNetwork` binding** — mixer holds `BlockPos` of its two decks; resolved lazily, validated on load, unbound if the block is gone.

---

## 4. Data flow

### Deck position simulation (model A — server-authoritative state, client-simulated playback)
- BE stores `playState`, `startEpochMs`, `offsetMs`, `rate`.
- Expected position on client = `(now - startEpochMs) * rate + offsetMs` while PLAY; frozen at `offsetMs` while PAUSE/CUE.
- Client's `AudioBackend` player free-runs; client hard-seeks the player only when `|playerPos - expectedPos| > DRIFT_THRESHOLD (~200 ms)`.
- Jog nudge = temporary `rate` delta for a few ticks (tempo bend). Coarse scrub = direct `offsetMs` set → seek.
- On relog / chunk load, client seeks once to expected position → resumes.

### Mixing
- Per deck: `deckVol = channelFader * crossfaderCurve(deck) * master`, all in `[0,1]`.
- `crossfaderCurve` = configurable curve (linear v1). Mixer recomputes on any fader change and syncs each deck's `volume`; client applies to that deck's player.

### Control → DMX
- On mixer control change (fader move / button), if a `dmxMap` entry exists for that control, mixer calls `DmxBridge.send(universe, channel, scaledValue)`.
- BPM tap: user taps a button; mixer derives BPM + phase; a server tick timer pushes a periodic value (e.g. strobe/intensity) to mapped light channels.

---

## 5. Networking (NeoForge 1.21.1 payload system)

- Register via `RegisterPayloadHandlersEvent` → `PayloadRegistrar`.
- Payloads implement `CustomPacketPayload` with `StreamCodec`.
- **C2S** (GUI → server): `SetFaderPayload`, `PressButtonPayload`, `JogNudgePayload`, `SetTrackUrlPayload`, `TransportPayload(play/cue/loop/fx)`, `SetDmxMapPayload`.
- **S2C** (server → clients with screen open): `DeckStatePayload`, `MixerStatePayload` for values not covered by the BE update tag (floats, url string).
- BE base state syncs via `getUpdateTag` / `getUpdatePacket` (chunk load); payloads handle live GUI updates and float precision (`ContainerData` is int-only).
- All C2S handlers validate: player is within interaction range of the block, block/BE exists, value ranges clamped.

---

## 6. GUI

- `AbstractContainerMenu` + `Screen` per block type; `MenuType`s registered.
- **CDJ screen:** circular jog-wheel widget (drag angle → nudge/scrub), play/cue/loop/FX buttons, tempo slider, track-URL text field, position/time readout, loop in/out markers.
- **Mixer screen:** 2 vertical channel faders, horizontal crossfader, master fader, per-deck transport/FX buttons, BPM-tap button + readout, DMX-map panel (assign each control to universe+channel), deck-link status/relink button.
- Custom widgets: `JogWheelWidget`, `FaderWidget`, `CrossfaderWidget` (drag interactions send throttled C2S payloads).

---

## 7. Registration & mod structure

- `DJBooth` main class (`@Mod("djbooth")`), event bus wiring.
- `ModBlocks`, `ModBlockEntities`, `ModItems`, `ModMenus`, `ModPayloads`, `ModSounds` deferred registers.
- Creative tab with the DJ blocks.
- Datagen: block/item models, blockstates, loot tables, lang (en_us + es_es), recipes.
- Soft-dep gating: `ModList.get().isLoaded("watermedia" / "minedmx")` before touching those APIs; interfaces default to no-op impls otherwise.

---

## 8. Build & tooling

- NeoForge MDK for 1.21.1 (NeoGradle), Java 21.
- `compileOnly`/`runtimeOnly` (or reflection) for WaterMedia and MineDMX so the mod compiles and loads without them present.
- Dev in a NeoForge dev workspace; test on a local NeoForge server mirroring the target (Create, MineDMX, WaterMedia installed) before touching the live server.

---

## 9. Phased plan (high level; detailed plan follows in writing-plans)

1. **Scaffold** — MDK, mod class, registration skeleton, one placeable block + BE + empty GUI. Runs in dev.
2. **Deck core** — `CdjBlockEntity` state, transport payloads, position sim (no audio yet), CDJ GUI transport + tempo + jog.
3. **Audio** — `AudioBackend` + `WaterMediaBackend`, wire deck state → VLC player, drift-seek, nudge/scrub. Degrade if absent.
4. **Mixer + linking** — `MixerBlockEntity`, faders/crossfader/master, deck binding, mixing math → deck volumes.
5. **DMX** — inspect MineDMX jar, implement `MineDmxBridge`, DMX-map GUI panel, BPM-tap light pulse.
6. **FX + polish** — 1–2 FX, SFX resource pack, models/textures, datagen, lang, recipes, degradation tests.
7. **Server test + (optional) publish** — install on staging server, verify with Create/MineDMX/WaterMedia, then live.

---

## 10. Open items
- **MineDMX jar inspection** (blocks phase 5 concrete impl only). Need the 1.5.1 jar on the dev PC or class signatures.
- WaterMedia API surface for EQ/filter FX (determines whether FX #1 is a real filter or a fallback gate) — verified in phase 3.
- Block models/textures art direction (functional placeholders first; polish in phase 6).
