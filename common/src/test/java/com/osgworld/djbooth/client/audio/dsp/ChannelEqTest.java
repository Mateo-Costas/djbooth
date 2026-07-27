package com.osgworld.djbooth.client.audio.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Artefact tests for the channel EQ.
 *
 * <p>These exist because the two faults this class was written to fix - a knob sweep clicking, and
 * boosted audio buzzing - were both found by ear after the fact, and both are perfectly measurable.
 * A click is a jump between neighbouring samples that the signal itself cannot account for. Hard
 * clipping is a flat top on the waveform. Neither needs a listener, so neither should ever have to
 * be reported by one again.
 */
class ChannelEqTest {
    private static final int FS = 48000;
    private static final int MONO = 1;

    /** A steady sine, the signal whose slew rate everything else is measured against. */
    private static double sine(int n, double freqHz) {
        return Math.sin(2 * Math.PI * freqHz * n / FS);
    }

    /**
     * Largest second difference in the output: how sharply the waveform bends, sample to sample.
     *
     * <p>Not the first difference. A sine at 440 Hz already moves 0.029 per sample, which is more
     * than a small coefficient jump displaces it by, so a step in the response hides inside the
     * signal's own slope. What a step cannot hide from is curvature: a smooth wave bends by
     * {@code A * omega^2} per sample and no more, while a discontinuity bends by the whole size of
     * the step. That is also what makes a click audible - the corner, not the travel.
     */
    private static double maxCurvature(double[] y) {
        double worst = 0;
        for (int i = 2; i < y.length; i++) {
            worst = Math.max(worst, Math.abs(y[i] - 2 * y[i - 1] + y[i - 2]));
        }
        return worst;
    }

    /** Run {@code frames} samples through the EQ, calling advance() on the real chunk cadence. */
    private static double[] run(ChannelEq eq, double[] in) {
        double[] out = new double[in.length];
        for (int n = 0; n < in.length; n++) {
            if (n % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            out[n] = eq.process(0, in[n]);
        }
        return out;
    }

    private static ChannelEq eqAt(float lowV, float midV, float highV) {
        ChannelEq eq = new ChannelEq();
        eq.setup(FS, MONO);
        eq.setTargets(lowV, midV, highV, false);
        return eq;
    }

    @Test
    void flatKnobsPassAudioThrough() {
        ChannelEq eq = eqAt(0.5f, 0.5f, 0.5f);
        double[] in = new double[FS / 10];
        for (int n = 0; n < in.length; n++) {
            in[n] = sine(n, 440) * 0.5;
        }
        double[] out = run(eq, in);
        // Allow for the filters' first few samples of settling, then demand a real match.
        for (int n = 64; n < in.length; n++) {
            assertEquals(in[n], out[n], 1e-6, "flat EQ altered the signal at sample " + n);
        }
    }

    // The knob does not arrive as a smooth ramp. PanelKnob moves it one wheel notch at a time, and
    // a notch is 0.04 of the travel: that discrete jump is what the coefficients have to absorb.
    private static final float WHEEL_NOTCH = 0.04f;

    // The tone has to sit inside the band being swept, or the test measures nothing. Moving LOW
    // while a 440 Hz tone plays barely disturbs it - the unsmoothed EQ scored 1.03x natural
    // curvature there, indistinguishable from clean. At 60 Hz, where the low shelf actually works,
    // the same fault scores 11.4x. Test each band where it lives.
    private static final double TONE_HZ = 60;
    private static final double TONE_AMP = 0.5;

    // One UI frame at 60 Hz is 800 audio frames: the fastest the mixer can actually move a target,
    // however hard the wheel is spun.
    private static final int FRAMES_PER_UI_FRAME = 800;

    /**
     * How sharply the bare test tone bends, times a margin.
     *
     * <p>Measured, not guessed. With the ramp in place a sweep peaks at 1.98x this tone's own
     * curvature and a hard one at 2.39x; with the coefficients jumping straight to each new notch
     * it is 11.4x. Three sits clear of both.
     */
    private static double curvatureCeiling() {
        double[] tone = new double[FS / 10];
        for (int n = 0; n < tone.length; n++) {
            tone[n] = sine(n, TONE_HZ) * TONE_AMP;
        }
        return 3 * maxCurvature(tone);
    }

    /**
     * Turn LOW from flat to full boost in wheel notches, {@code framesPerNotch} apart, starting at
     * sample {@code phase} so the jump lands at a different point of the waveform each run.
     * Returns the sharpest bend in the output.
     */
    private static double sweepWorstCurvature(int phase, int framesPerNotch) {
        ChannelEq eq = eqAt(0.5f, 0.5f, 0.5f);
        int frames = FS / 2;
        double[] out = new double[frames];
        float knob = 0.5f;
        for (int n = 0; n < frames; n++) {
            if (n >= phase && (n - phase) % framesPerNotch == 0 && knob < 1.0f) {
                knob = Math.min(1.0f, knob + WHEEL_NOTCH);
                eq.setTargets(knob, 0.5f, 0.5f, false);
            }
            if (n % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            out[n] = eq.process(0, sine(n, TONE_HZ) * TONE_AMP);
        }
        return maxCurvature(out);
    }

    @Test
    void sweepingAKnobDoesNotClick() {
        // The regression that started this: LOW swept up while a tone plays. Rebaking straight onto
        // each new notch steps the filter's response between one sample and the next, and that step
        // is the click. How big it is depends where in the waveform the notch lands, so sweep at
        // many phases and take the worst rather than trusting one lucky alignment.
        double ceiling = curvatureCeiling();
        double worst = 0;
        int worstPhase = -1;
        for (int phase = 0; phase < 64; phase++) {
            double curv = sweepWorstCurvature(phase, FRAMES_PER_UI_FRAME);
            if (curv > worst) {
                worst = curv;
                worstPhase = phase;
            }
        }
        assertTrue(worst < ceiling,
                "a knob sweep bent the waveform by " + worst + " (phase " + worstPhase
                        + "), over the " + ceiling + " the tone can account for: the EQ is clicking");
    }

    @Test
    void aFastSweepDoesNotClickEither() {
        // Same again but with the targets arriving three times faster than the UI can send them,
        // which gives the ramp far less room. Headroom against a cadence that cannot happen.
        double ceiling = curvatureCeiling();
        double worst = 0;
        for (int phase = 0; phase < 64; phase++) {
            worst = Math.max(worst, sweepWorstCurvature(phase, FRAMES_PER_UI_FRAME / 3));
        }
        assertTrue(worst < ceiling,
                "a fast knob sweep bent the waveform by " + worst + ", over " + ceiling);
    }

    @Test
    void aJumpStraightToFullBoostStillRamps() {
        // Not a sweep but a snap: right-click reset, or a preset load. The ramp has to absorb the
        // whole travel at once. Again, try every phase rather than one.
        double ceiling = curvatureCeiling();
        double worst = 0;
        for (int phase = 0; phase < 64; phase++) {
            ChannelEq eq = eqAt(0.5f, 0.5f, 0.5f);
            int frames = FS / 4;
            double[] out = new double[frames];
            for (int n = 0; n < frames; n++) {
                if (n == 1024 + phase) {
                    eq.setTargets(1.0f, 0.5f, 0.5f, false); // slam LOW to full boost
                }
                if (n % ChannelEq.CHUNK_FRAMES == 0) {
                    eq.advance();
                }
                out[n] = eq.process(0, sine(n, TONE_HZ) * TONE_AMP);
            }
            worst = Math.max(worst, maxCurvature(out));
        }
        assertTrue(worst < ceiling,
                "slamming a knob bent the waveform by " + worst + ", over " + ceiling);
    }

    @Test
    void firstBlockSnapsToTheKnobsInsteadOfSweepingUp() {
        // Loading a deck whose EQ is already turned must not sweep audibly from flat up to it.
        ChannelEq ramped = new ChannelEq();
        ramped.setup(FS, MONO);
        ramped.setTargets(1.0f, 1.0f, 1.0f, false);
        ramped.advance();
        double first = ramped.process(0, 1.0);

        ChannelEq settled = eqAt(1.0f, 1.0f, 1.0f);
        for (int i = 0; i < 200; i++) {
            settled.advance();
        }
        double later = settled.process(0, 1.0);
        assertEquals(later, first, 1e-9, "the EQ started flat and swept up to its loaded position");
    }

    @Test
    void eachBandActsOnTheFrequencyPrintedOnTheHardware() {
        // The bands used to sit at 200 / 632 / 2000 Hz while claiming to be a DJM-900NXS2, whose
        // published centres are 70 / 1000 / 13000. Every band being in the wrong place is why each
        // one sounded wrong in a different way. Check the gain each band applies at its own centre
        // and, just as importantly, that it leaves the other bands' centres roughly alone.
        assertEquals(70.0, ChannelEq.F_LOW, 1e-9);
        assertEquals(1000.0, ChannelEq.F_MID, 1e-9);
        assertEquals(13000.0, ChannelEq.F_HIGH, 1e-9);

        // A shelf's quoted frequency is the middle of its transition, where it applies half its
        // gain — so +6 dB of LOW measures +3 dB at 70 Hz and reaches full lift below it. Measure
        // each shelf in its passband, not at its corner, or the test asks for something no shelf
        // can do. (Getting this wrong is what made this test fail when the bands were corrected.)

        // LOW boosted: full lift well below 70 Hz, +3 dB at the corner, nothing up top.
        assertEquals(2.0, gainAt(1f, 0.5f, 0.5f, 25), 0.15, "LOW +6 in its passband");
        assertEquals(1.41, gainAt(1f, 0.5f, 0.5f, 70), 0.1, "a shelf applies half its gain at F");
        assertTrue(gainAt(1f, 0.5f, 0.5f, 13000) < 1.1, "LOW must not reach the top end");

        // HI boosted: full lift above 13 kHz, nothing in the bass.
        assertEquals(2.0, gainAt(0.5f, 0.5f, 1f, 20000), 0.2, "HI +6 in its passband");
        assertTrue(gainAt(0.5f, 0.5f, 1f, 70) < 1.1, "HI must not reach the bass");

        // MID is a bell, so it does hit its full gain at its own centre.
        assertEquals(2.0, gainAt(0.5f, 1f, 0.5f, 1000), 0.15, "MID +6 at 1 kHz");
        assertTrue(gainAt(0.5f, 1f, 0.5f, 60) < 1.3, "MID must not swamp the bass");
    }

    /** Steady-state gain the EQ applies to a sine at {@code hz}, once the ramps have settled. */
    private static double gainAt(float lo, float mid, float hi, double hz) {
        double fs = 48000;
        ChannelEq eq = new ChannelEq();
        eq.setup((int) fs, 1);
        eq.setTargets(lo, mid, hi, false);
        for (int i = 0; i < 500; i++) {
            eq.advance();
        }
        int n = (int) (fs / 2);
        double peakIn = 0, peakOut = 0;
        for (int i = 0; i < n; i++) {
            double s = Math.sin(2 * Math.PI * hz * i / fs);
            double y = eq.process(0, s);
            if (i > n / 2) { // let the filter settle before measuring
                peakIn = Math.max(peakIn, Math.abs(s));
                peakOut = Math.max(peakOut, Math.abs(y));
            }
        }
        return peakOut / peakIn;
    }

    @Test
    void theTrimKnobIsEvenHandedInBothDirections() {
        // A linear "knob * 2" law spends its entire lower half between silence and unity, so the
        // bottom of the travel collapsed to nothing while the top hardly moved. In dB the two
        // halves are comparable, and the centre is exactly unity.
        assertEquals(1.0, ChannelEq.trimGain(0.5), 1e-9, "centre must be unity gain");
        assertEquals(0.0, ChannelEq.trimGain(0.0), 1e-12, "fully down must be silence");

        double up = 20 * Math.log10(ChannelEq.trimGain(0.54));
        double down = 20 * Math.log10(ChannelEq.trimGain(0.46));
        assertTrue(up > 0.2 && up < 1.5, "a notch up should be a small lift, was " + up + " dB");
        assertTrue(down < -1.0, "a notch down should be a real cut, was " + down + " dB");

        // Monotonic all the way, with no jump at the centre.
        double prev = -1;
        for (double v = 0; v <= 1.0001; v += 0.005) {
            double g = ChannelEq.trimGain(v);
            assertTrue(g >= prev, "trim dipped at " + v);
            prev = g;
        }
        assertTrue(ChannelEq.trimGain(1.0) <= 2.01, "trim should top out near +6 dB");
    }

    @Test
    void extremeSettingsStayFiniteAndBounded() {
        // Every corner of the knob travel, both EQ curves, driven with full-scale noise: no NaN,
        // no infinity, no runaway. An unstable biquad shows up immediately here.
        java.util.Random rng = new java.util.Random(7);
        float[] positions = {0f, 0.25f, 0.5f, 0.75f, 1f};
        for (boolean iso : new boolean[] {false, true}) {
            for (float l : positions) {
                for (float m : positions) {
                    for (float h : positions) {
                        ChannelEq eq = new ChannelEq();
                        eq.setup(FS, MONO);
                        eq.setTargets(l, m, h, iso);
                        for (int n = 0; n < 4096; n++) {
                            if (n % ChannelEq.CHUNK_FRAMES == 0) {
                                eq.advance();
                            }
                            double y = eq.process(0, rng.nextDouble() * 2 - 1);
                            assertTrue(Double.isFinite(y),
                                    "EQ went non-finite at low=" + l + " mid=" + m + " high=" + h
                                            + " isolator=" + iso);
                            assertTrue(Math.abs(y) < 64,
                                    "EQ ran away to " + y + " at low=" + l + " mid=" + m
                                            + " high=" + h + " isolator=" + iso);
                        }
                    }
                }
            }
        }
    }

    @Test
    void silenceInGivesSilenceOut() {
        // The anti-denormal bias in Biquad is a constant added to the state every sample. If it is
        // ever raised carelessly it becomes audible DC on a quiet channel, which is a hum.
        ChannelEq eq = eqAt(1.0f, 1.0f, 1.0f);
        double worst = 0;
        for (int n = 0; n < FS; n++) {
            if (n % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            worst = Math.max(worst, Math.abs(eq.process(0, 0.0)));
        }
        assertTrue(worst < 1e-9, "silence produced a DC offset of " + worst);
    }

    @Test
    void isolatorCutsHarderThanEqCut() {
        assertTrue(ChannelEq.dbForBand(0.0, true) < ChannelEq.dbForBand(0.0, false),
                "ISOLATOR must kill the band harder than the EQ curve cuts it");
        assertEquals(0.0, ChannelEq.dbForBand(0.5, false), 1e-12, "centre detent must be flat");
        assertEquals(0.0, ChannelEq.dbForBand(0.5, true), 1e-12, "centre detent must be flat");
        assertEquals(ChannelEq.EQ_BOOST_DB, ChannelEq.dbForBand(1.0, false), 1e-12);
        assertEquals(-ChannelEq.EQ_CUT_DB, ChannelEq.dbForBand(0.0, false), 1e-12);
    }

    @Test
    void bandsActuallyMoveTheirOwnFrequencies() {
        // Guards against a wiring slip: LOW must lift bass and leave treble alone, HI the reverse.
        // Measured inside each shelf's passband: the HI shelf sits at 13 kHz now, so 10 kHz is on
        // its slope rather than past it, and asking for full lift there fails a correct filter.
        assertTrue(bandGain(1.0f, 0.5f, 0.5f, 40) > 1.5, "LOW boost did not lift 40 Hz");
        assertTrue(bandGain(1.0f, 0.5f, 0.5f, 10000) < 1.1, "LOW boost leaked into 10 kHz");
        assertTrue(bandGain(0.5f, 0.5f, 1.0f, 19000) > 1.5, "HI boost did not lift 19 kHz");
        assertTrue(bandGain(0.5f, 0.5f, 1.0f, 60) < 1.1, "HI boost leaked into 60 Hz");
        assertTrue(bandGain(0.0f, 0.5f, 0.5f, 40) < 0.2, "LOW cut did not remove 40 Hz");
    }

    /** Settled output amplitude at one frequency, relative to a unit-amplitude input. */
    private static double bandGain(float lowV, float midV, float highV, double freqHz) {
        ChannelEq eq = eqAt(lowV, midV, highV);
        int frames = FS / 2;
        double peak = 0;
        for (int n = 0; n < frames; n++) {
            if (n % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            double y = eq.process(0, sine(n, freqHz));
            if (n > frames / 2) { // ignore the ramp and the filters settling
                peak = Math.max(peak, Math.abs(y));
            }
        }
        return peak;
    }
}

