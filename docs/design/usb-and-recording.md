# USB sticks and recording — analysis, not a plan to build yet

Two separate features that sound like one. Worth separating before deciding, because one is
straightforward and the other has a problem no amount of code solves.

---

## 1. The USB stick: carry your tracks and your cue points

This is the one that matters, and it is the smaller job.

### What it is in real life

A DJ turns up with a USB stick. On it: audio files, plus a rekordbox database holding, per track,
the cue point, the hot cues, the loops, the beat grid, the BPM and the key. Plug it into a CDJ, the
screen lists the playlists, load a track, and every marker the DJ set at home is already there.

That is the actual value. Not the audio — the *preparation*.

### What it is here

An item. Right-click a CDJ with it to insert; the deck's browse list becomes what is on the stick.

The stick holds a list of entries, each one:

```
url            the stream, exactly what the URL box takes today
title          what to show in the list
cuePointMs     where CUE jumps to
hotCues[4]     the four hot cues
loopIn/OutMs   a saved loop
bpm, key       so BEAT SYNC and KEY SYNC work the moment it loads
```

Every one of those fields already exists on `DeckState` and already round-trips through NBT. The
feature is mostly plumbing: a new item, a component holding a list of the above, a browse screen,
and a "save current track to stick" action.

### Why it fits this mod specifically

- **It survives the world.** Right now a set lives in whoever's client memory until they close the
  GUI. The `RECENTS` list is static and client-side; it does not even survive a restart.
- **It is tradeable.** One player prepares a set, hands the stick to another player, and it works.
  That is exactly the real-world social object, and Minecraft is already a game about items.
- **It makes the existing work pay off.** Hot cues, loops, key sync and beat sync are all built and
  all forgotten the moment a track is unloaded. A stick is what makes preparing a track worth doing.
- **No new dependencies.** No audio path changes, no WaterMedia involvement, no new licence
  question.

### Shape of the work

| Piece | Notes |
|---|---|
| `UsbStickItem` + data component | A record list. Data components are the 1.21 way; no NBT tags by hand. |
| Insert / eject | A slot on `CdjBlockEntity`, or hold-and-right-click. A slot is more real and shows in the world. |
| Browse screen | A list on the CDJ screen. The screen now has a proper black panel to draw it on. |
| Save-to-stick | One button: writes the deck's current markers to the stick's entry. |
| Capacity | Real sticks fill up. A cap of, say, 64 tracks is flavour and stops a stick becoming a database. |

Two decisions to make before starting, both cheap to get wrong and expensive to change:

1. **Does the stick store the URL or a track id?** URL is simpler and works today. But a stick full
   of raw YouTube links is brittle — links die. No good answer; probably URL, and accept it.
2. **Per-stick or per-track markers?** If two sticks hold the same URL with different cue points,
   which wins? Real rekordbox is per-stick. Match that: the markers live on the stick's entry, not
   on some global table.

**Verdict: worth building.** Highest value per unit of work of anything left on this mod.

---

## 2. Recording the mix

Different story.

### Technically: yes, and closer than expected

The DSP already has the audio. `DspSfxEngine.upload(ByteBuffer)` sees every decoded PCM block of
every deck, after EQ, after COLOR, after BEAT FX. Writing that to a WAV file is a few dozen lines.

The catch is that it is *per deck*. Each deck owns its own engine and its own OpenAL source, and the
mixing happens inside OpenAL, downstream of anything this mod can see. So `upload` cannot capture
the master — only one channel at a time, already faded but never summed.

Three ways round it, in increasing order of honesty and effort:

**(a) Sum in Java before OpenAL.** Both decks' engines write into a shared master buffer, and the
capture reads that. Correct output, but the two decks decode on their own threads at their own
rates, so this needs a real ring buffer with timestamps to line them up. It is the piece of work
where a mistake produces a recording that slowly drifts out of sync with itself.

**(b) Record each deck separately.** Two files, the DJ sums them elsewhere. Much simpler, genuinely
useful for editing, but it is not "record my set".

**(c) Re-render offline.** Store the events (what played, when, every knob move) and rebuild the
audio afterwards from the source streams. Perfect quality, no realtime constraint, and the event log
is tiny. But it needs the streams to still exist when you render, and it is by far the most code.

Storage, for scale: stereo, 48 kHz, 16-bit is about **11 MB per minute**. An hour-long set is 660 MB
of WAV sitting in the game directory. Any real version needs either a cap, or encoding to something
compressed, which means another dependency.

### The problem code does not solve

The audio comes from YouTube. Recording it to a file on disk is not the same act as streaming it,
and a feature whose purpose is to produce durable copies of copyrighted recordings is a different
thing from a feature that plays them.

That is not a reason to abandon it, but it decides the shape:

- **Recording your own knob moves and mix decisions: fine.** That is your performance.
- **Writing a WAV of somebody's master to disk: needs thought,** and at minimum should not be the
  path of least resistance for a user who just wanted to hear their mix back.

Option (c) sidesteps most of this neatly, which is worth noting: an event log records what *you*
did — every fader, every cue, every effect — and is a few kilobytes. Play it back through the same
decks and you hear your set again, without ever producing a file of the music itself. It is also,
incidentally, the most useful format: you can edit it, and you can hand it to someone else and they
can watch the booth play itself.

### Shape of the work

| Approach | Effort | Output | Concern |
|---|---|---|---|
| (b) per-deck WAV | small | two files | copies of source audio |
| (a) master WAV | large | one file | copies, plus drift risk |
| (c) event log | medium | a few KB | none of the above |

**Verdict: build (c), not (a) or (b).** A "recording" that replays your set through the decks is
smaller, safer, more useful, and more in the spirit of a Minecraft mod than a folder full of
660 MB WAVs. It also composes with the USB: **the stick is where the recording is stored.** Hand
someone your stick and they can play your set back on their own booth.

---

## Recommended order

1. **USB stick with track markers.** Self-contained, makes existing features matter, no open
   questions.
2. **Set recording as an event log, saved to the stick.** Builds directly on 1, and the
   `TransportPayload` / `MixerPayload` stream is already the event log — it just needs capturing
   with timestamps.
3. **Audio export.** Only if there is a real reason, and only after deciding what it is for.

Nothing here is started. Numbers above are measurements or arithmetic, not estimates: the 11 MB per
minute is from the format, and the DSP interception point is where the existing EQ already runs.
