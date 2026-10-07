# DJM-900NXS2 reference: what the manual says, and where the mod does it

The booth is modelled on the Pioneer DJ DJM-900NXS2. Nobody working on this mod can judge the
sound by ear, so the mixer is built from published descriptions instead, and this file is the audit
trail: for each statement, a short quote with its page, the code that follows it, and the test that
would fail if the code stopped following it.

**Sources**

- Pioneer DJ, *DJM-900NXS2 Operating Instructions* (the French and Spanish editions, read in parallel;
  page numbers below are the printed ones, "p." = French edition). Not included here: it is
  Pioneer's text. Download it from the Pioneer DJ support site.
- Rane Corporation, *RaneNote 146: Evolution of the DJ Mixer Crossfader* (Rick Jeffs, 1999), for the
  shapes of crossfader tapers.
- The Mixxx manual, "Equalizers", for how an open-source DJ program builds an isolator.

Quotes are kept to a few words each and translated. Where a number below is an **estimate**, it is
marked as one in the code and in the last section here.

## Signal path

| What the manual says | Code | Test |
| --- | --- | --- |
| Channel level indicator shows the level "avant de passer par les faders de canaux" (before the channel faders). p.8, item k | `ChannelStrip.filter` reads the meter after COLOR FX and before the fader gain | `ChannelStripTest.theChannelMeterReadsBeforeTheFader` |
| On DELAY, ECHO, PING PONG, SPIRAL, REVERB: lowering the faders leaves the delayed / echo sound ("entraînent un son retardé / d'écho ... un fondu"). p.16 | The channel fader gain is applied inside `ChannelStrip`, ahead of `BeatFx`. Out of the strip, only the crossfader and master remain (OpenAL volume) | `ChannelStripTest.closingTheFaderLeavesTheEffectsTailRinging` (fails if the fader is moved after the effect) |
| Block diagram: `Ch Fader / BEAT FX` after `Trim / EQ / SOUND COLOR FX`. p.14, p.25 | Order in `ChannelStrip`: trim, key lock, EQ, COLOR FX, meter, fader, BEAT FX, echo, balance, limiter | `ChannelStripTest` as above |
| BEAT FX can target a channel (1 to 4), CROSS FADER A, CROSS FADER B or MASTER. p.13 | `MixLevels.gains(effectOnMaster, ...)`: an effect on the deck's own channel sits before the crossfader; one on MASTER sits after the whole mix | `MixLevelsTest.aBeatFxOnTheChannelComesAfterTheFaderButAheadOfTheCrossfader`, `aBeatFxOnMasterComesAfterTheWholeMix` |
| The normal mix is unchanged by all of this | `Gains.pre * Gains.post` equals the old `channelVolume` for every fader, crossfader, master, curve and routing | `MixLevelsTest.splitGainsMultiplyToTheVolumeTheChannelAlwaysHad` |
| MASTER LEVEL acts on `MASTER1`, `MASTER2` and `DIGITAL MASTER OUT` (p.8, item p); BALANCE acts on those and on `BOOTH`, `REC OUT`, `PHONES` (p.12) | The booth listener's gain does not include master level | `MixLevelsTest.theBoothIsNotScaledByMasterLevel` |
| Pressing CUE sends the channel to the headphones (p.11). The block diagram (p.25) taps `CUE CHx` ahead of the `CH Fader`; the text does not say "pre-fader" in words, so this is read from the diagram | A cued channel is heard at the booth with its fader bypassed (`pre = 1`), and uncued channels drop out | `MixLevelsTest.cueingAChannelPreviewsItAtTheBoothAndMutesTheRest` |
| Master section has a PEAK LIMITER, on by default ("Réduit l'écrêtage numérique"). p.22 | `Limiter` at the end of every `ChannelStrip` (one per deck: the engines are separate, see "Known deviations") | `LimiterTest` |
| Set TRIM so the channel indicator peaks near 0 dB; set MASTER LEVEL so the master indicator peaks near 0 dB. p.23 | The panel meter is in dB with 0 dB at the top (`BoothScreen.drawMeter`) | none (display only) |

## Channel EQ and isolator

| What the manual says | Code | Test |
| --- | --- | --- |
| EQ range "-26 dB a +6 dB". p.11 | `ChannelEq.EQ_BOOST_DB = 6`, `EQ_CUT_DB = 26` | `ChannelEqTest` |
| `EQ CURVE` switch: ISOLATOR "fonctionne comme isolateur", EQ "la fonction d'egalisation". p.11 | `ChannelEq.dbForBand(knob, isolator)`: the bottom of the throw is a full kill in ISOLATOR mode | `ChannelEqTest`, `EqStructureDiagnosticsTest` |
| Retail spec sheets: ISOLATOR range "-inf to +6 dB" | kill gain is exactly 0 at the bottom of the knob in ISOLATOR mode | `ChannelEqTest` |
| The isolator is "adapted from analog crossover networks" (Mixxx manual); a Linkwitz-Riley split is the standard way to build one | `Crossover`: two cascaded Butterworth sections per side, splits at 250 Hz and 4 kHz | `EqStructureDiagnosticsTest` |

## Fader and crossfader curves

| What the manual says | Code | Test |
| --- | --- | --- |
| CH FADER has three positions: one "s'eleve subitement a l'arriere" (rises suddenly at the far end), one gradual, one rising strongly at the front. p.11 | `MixLevels.curve`: SHARP (cube), LIN, SLOW (square root) | `MixLevelsTest.theCurvesAreShapedHardEnoughToBeWorthHaving` and the other channel-curve tests |
| CROSS FADER has three positions: fast rising ("les signaux audio sortent immediatement du cote B"), an intermediate one, and a gradual one where B rises as A falls. p.11 | `MixLevels.crossfaderSide`: FAST, MEDIUM and SLOW | `MixLevelsTest` (crossfader section) |
| Classic constant-power taper, a medium "cut in and pump it up" taper, a sharp "cut and scratch" taper (Rane Figures 1, 3, 4) | SLOW is `sin` / `cos` (powers sum to one; -3 dB each at the centre). FAST leaves both sides at full across the middle. MEDIUM is between | `slowIsAConstantPowerBlend`, `fastKeepsBothSidesAtFullAcrossTheMiddle`, `mediumSitsBetweenSlowAndFastAtEveryPoint`, `everyCrossfaderCurveOnlyEverRisesTowardItsOwnSide` |
| THRU: "les signaux ne passent pas par le crossfader". p.11 | `XF_THRU` weight is always 1 | `thruTakesAChannelOffTheCrossfaderEntirely` |
| Saves made before the curves changed | `MixerBlockEntity.readCrossFaderCurve` maps the old indices to the closest new meaning | `MixerCurveMigrationTest` |

## SOUND COLOR FX (p.16)

| Manual | Code | Test |
| --- | --- | --- |
| FILTER: COLOR left lowers a low-pass cutoff, right raises a high-pass; PARAMETER right "augmente la resonance" | `ColorFx.bake`, case FILTER | `ColorFxTest.filterLeftCutsTrebleAndRightCutsBass`, `ColorFxArtifactTest` |
| SWEEP: left is a gate and PARAMETER sets how hard it closes; right is a band pass whose width narrows with COLOR, and **PARAMETER sets its centre frequency** (right = higher) | `ColorFx.bake` (case SWEEP) and `ColorFx.gate` | `ColorFxTest.sweepRightPutsTheBandPassWhereParameterSaysAndOnlyThere`, `sweepRightCentreRisesMonotonicallyWithParameter`, `sweepLeftGateClosesHarderAsParameterGoesUp` |
| DUB ECHO: echo applied to the **mids only** with COLOR left, to the **highs only** with COLOR right; PARAMETER right raises the return | `ColorFx.bake` (case DUB_ECHO): band-pass send on the left, high-pass on the right | `ColorFxTest.dubEchoLeftRepeatsOnlyTheMidsAndRightOnlyTheHighs` |
| SPACE: reverb to mids and lows on the left, to mids and highs on the right; PARAMETER right raises the return | `ColorFx.bake` (case SPACE) | `ColorFxTest.reverbAndEchoRingOnAfterTheInputStops` |
| NOISE: white noise through a filter whose cutoff drops on the left and rises on the right; PARAMETER raises the noise level | `ColorFx.process` (case NOISE) | `ColorFxTest.noiseAddsSignalToSilence` |
| CRUSH: more distortion on the left; on the right the sound is crushed and then high-passed | `ColorFx.crush` | `ColorFxArtifactTest` |
| Dead centre is always dry | `ColorFx` returns the input when `depth <= 0` | `ColorFxTest.centreDetentPassesAudioThroughUntouched` |

## BEAT FX (p.13 to p.18)

| Manual | Code | Test |
| --- | --- | --- |
| LEVEL/DEPTH on DELAY, ECHO, PING PONG, REVERB, ROLL: "regle la balance entre le son original et l'echo". So full depth is wet only. | `BeatFx.process`: `dry + send + depth * (wet - send)` | `BeatFxTest` |
| LEVEL/DEPTH fully left leaves "le son original seulement" (SPIRAL, FLANGER, PHASER and others) | the stage returns the input when depth is zero | `BeatFxTest.depthAtZeroIsAlsoDry` |
| The 14 effects, in selector order | `BeatFxTypes.NAMES` | `ChannelSettingsTest.everyBeatFxAndColourModeIsNamed` |
| BEAT buttons: 1/16, 1/8, 1/4, 1/2, 3/4, 1, 2, 4 | `BeatFxTypes.BEATS` | `ChannelSettingsTest.beatFractionsRunSmallestToLargest` |
| FX FREQUENCY: apply the effect to the lit band(s) only | `BeatFx.process` band split | `BeatFxTest` |
| TIME up to 16000 ms (TRANS) and 32000 ms (FLANGER, PHASER, FILTER) | `BeatFx.maxSecondsFor` | `BeatFxTest.sweepingEffectsMayRunToThirtyTwoSeconds` |
| TIME up to 4000 ms for the delays and loops | **Not enforced**, on purpose: see "Known deviations" | `BeatFxTest.theLongestEffectTimeThePanelCanAskForStillDelays` |
| The AUTO BPM range is 70 to 180; TAP can set others | `MixerBlockEntity.setBpm` accepts 40 to 300 | none |

## Known deviations

These are places where the mod differs from the hardware, with the reason.

- **One engine per deck, not a shared bus.** The audio of each deck is processed on the client of
  every listener by its own player, and OpenAL sums them. The hardware mixes first and then applies
  the master limiter and a MASTER-patched BEAT FX to the sum. Here the limiter and a MASTER effect
  run once per deck. For the linear effects this is equivalent; for the limiter it is not (two decks
  at full can exceed what a limiter on the sum would allow).
- **The delays and loops are not capped at 4000 ms.** That is the range of a TIME knob this panel
  does not have. Here the time is the BPM times a beat fraction, and at 40 to 60 BPM a four-beat
  fraction needs up to six seconds; capping it would put the repeat off the beat.
- **The crossfader and master level are applied by OpenAL's volume**, once per game tick, so very
  fast crossfader moves step slightly. The channel fader is smoothed per sample (about 7 ms).
- **No MIC, SEND/RETURN, talk-over or recording outputs.**
- **No real BPM detection.** The hardware measures BPM automatically (70 to 180) and reads rekordbox
  grid data. The mod takes a tapped tempo or a GetSongBPM lookup.

## Estimates (numbers the sources do not give)

- Crossfader shapes. The manual and Rane describe them; neither gives numbers for a DJM. The MEDIUM
  curve reaches full at 55% of travel and FAST opens over 6% (`MixLevels`). The 6% comes from
  Rane's stated goal of a taper "less than .1 inch" wide, which is about 5.5% of a 45 mm fader;
  that is Rane's target, not Pioneer's.
- SWEEP's band-pass centre range, 200 Hz to 8 kHz (`ColorFx`). The manual gives the direction only.
- DUB ECHO's send corners: mids 250 Hz to 2.5 kHz, highs above 2.5 kHz (`ColorFx`).
- The isolator's split points, 250 Hz and 4 kHz. The printed frequencies are where each band's gain
  is *quoted*: the DJM-900 manual and retailers give 70 Hz, 1 kHz and 13 kHz, while the DJM-900NXS2
  manual prints 20 Hz, 1 kHz and 30 kHz. 250 Hz and 4 kHz have their geometric centre at exactly 1 kHz,
  the mid band's quoted point. Mixxx splits at 246 Hz and 2.5 kHz. No measurement of the hardware's
  crossover was found.
- The booth tap. The manual shows BOOTH on its own level control and not under MASTER LEVEL; that it
  is independent of MASTER LEVEL is read from that, not stated outright.
- The trim range (+6 dB / -26 dB) is matched to the EQ's. The manual describes the knob's purpose
  (set the channel indicator to peak near 0 dB), not its range.
