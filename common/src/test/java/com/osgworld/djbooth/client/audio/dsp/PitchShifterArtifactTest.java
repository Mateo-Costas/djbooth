package com.osgworld.djbooth.client.audio.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Artefact tests for MASTER TEMPO's pitch shifter.
 *
 * <p>The shifter reads a delay line at a different speed from the one it is written at, so its read
 * taps wrap, and every wrap is a discontinuity. The whole design exists to hide those wraps behind
 * a crossfade; these tests check that it does, at the ratios a tempo fader actually produces, and
 * that it is honest about being bypassed.
 */
class PitchShifterArtifactTest {
    private static final int FS = 48000;
    private static final double TONE_HZ = 220;
    private static final double TONE_AMP = 0.5;

    private static double sine(int n, double f) {
        return Math.sin(2 * Math.PI * f * n / FS);
    }

    private static double maxCurvature(double[] y, int from) {
        double worst = 0;
        for (int i = Math.max(2, from); i < y.length; i++) {
            worst = Math.max(worst, Math.abs(y[i] - 2 * y[i - 1] + y[i - 2]));
        }
        return worst;
    }

    private static double naturalCurvature() {
        double[] tone = new double[FS / 10];
        for (int n = 0; n < tone.length; n++) {
            tone[n] = sine(n, TONE_HZ) * TONE_AMP;
        }
        return maxCurvature(tone, 2);
    }

    private static double[] run(double ratio, int frames) {
        PitchShifter ps = new PitchShifter();
        ps.setup(FS);
        ps.setRatio(ratio);
        double[] out = new double[frames];
        for (int n = 0; n < frames; n++) {
            out[n] = ps.process(sine(n, TONE_HZ) * TONE_AMP);
        }
        return out;
    }

    @Test
    void unityRatioIsExactPassthrough() {
        PitchShifter ps = new PitchShifter();
        ps.setup(FS);
        ps.setRatio(1.0);
        for (int n = 0; n < FS; n++) {
            double in = sine(n, TONE_HZ) * TONE_AMP;
            assertEquals(in, ps.process(in), 0.0,
                    "the shifter coloured the signal while bypassed, at sample " + n);
        }
    }

    @Test
    void aRejectedRatioBypassesRatherThanBreaking() {
        // Zero and negative ratios are nonsense; the deck must not be able to hand one over and
        // get silence or a division by zero back.
        for (double bad : new double[] {0.0, -1.0, -0.5}) {
            PitchShifter ps = new PitchShifter();
            ps.setup(FS);
            ps.setRatio(bad);
            for (int n = 0; n < 1024; n++) {
                double in = sine(n, TONE_HZ) * TONE_AMP;
                assertEquals(in, ps.process(in), 0.0, "ratio " + bad + " was not bypassed");
            }
        }
    }

    @Test
    void wrapsAreNotAudibleAcrossTheTempoFaderRange() {
        // A CDJ tempo fader spans about +/-16%, which is the whole job: cancel that and no more.
        // Every wrap of the read taps happens inside this run, so a badly aligned crossfade shows.
        double ceiling = 4 * naturalCurvature();
        for (double pct = -16; pct <= 16.0001; pct += 2) {
            if (Math.abs(pct) < 0.5) {
                continue; // bypassed, covered above
            }
            double ratio = 1.0 / (1.0 + pct / 100.0);
            double[] out = run(ratio, FS * 2);
            // Skip the first buffer: the line starts empty, and that fade-in is not an artefact.
            double worst = maxCurvature(out, FS / 4);
            assertTrue(worst < ceiling,
                    "at " + pct + "% the shifter bent the waveform by " + worst + ", over " + ceiling
                            + ": a tap wrap is audible");
        }
    }

    @Test
    void levelIsHeldAcrossTheCrossfade() {
        // The two taps are summed with weights that must add to one. If they do not, the output
        // breathes: loud at the middle of each wrap cycle and thin at the handover, or the reverse.
        for (double pct : new double[] {-16, -8, -3, 3, 8, 16}) {
            double ratio = 1.0 / (1.0 + pct / 100.0);
            double[] out = run(ratio, FS * 2);
            double loudest = 0;
            double quietest = Double.MAX_VALUE;
            int window = FS / 20; // 50 ms, well under one wrap cycle
            for (int start = FS / 4; start + window < out.length; start += window) {
                double peak = 0;
                for (int i = start; i < start + window; i++) {
                    peak = Math.max(peak, Math.abs(out[i]));
                }
                loudest = Math.max(loudest, peak);
                quietest = Math.min(quietest, peak);
            }
            double ripple = loudest / quietest;
            assertTrue(ripple < 2.0,
                    "at " + pct + "% the level swung by " + ripple + "x across the crossfade cycle");
        }
    }

    @Test
    void extremeRatiosStayFiniteAndBounded() {
        // Well past what a tempo fader can ask for. The output is allowed to sound bad here; it is
        // not allowed to be infinite, or to leave the buffer.
        for (double ratio : new double[] {0.25, 0.5, 0.75, 1.5, 2.0, 4.0}) {
            double[] out = run(ratio, FS);
            for (int n = 0; n < out.length; n++) {
                assertTrue(Double.isFinite(out[n]), "ratio " + ratio + " went non-finite");
                assertTrue(Math.abs(out[n]) <= TONE_AMP * 1.5 + 1e-9,
                        "ratio " + ratio + " produced " + out[n] + " from a " + TONE_AMP + " input");
            }
        }
    }

    @Test
    void resetClearsTheLine() {
        PitchShifter ps = new PitchShifter();
        ps.setup(FS);
        ps.setRatio(1.06);
        for (int n = 0; n < FS; n++) {
            ps.process(sine(n, TONE_HZ));
        }
        ps.reset();
        double worst = 0;
        for (int n = 0; n < FS; n++) {
            worst = Math.max(worst, Math.abs(ps.process(0.0)));
        }
        assertTrue(worst < 1e-6, "the shifter kept playing old audio after reset, peaking at " + worst);
    }
}
