package com.osgworld.djbooth.client.audio.dsp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The output stage. What matters here is not that it stays inside full scale — a hard clip does
 * that — but that it leaves audio alone until it genuinely has to act, and colours nothing when
 * it does.
 */
class LimiterTest {

    private static final double FS = 48000;

    private static double[] sine(double hz, double amp, int n) {
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = amp * Math.sin(2 * Math.PI * hz * i / FS);
        }
        return x;
    }

    /** Distortion as a fraction of the fundamental, by projecting the output onto the input tone. */
    private static double thd(double[] out, double hz) {
        int n = out.length;
        double re = 0, im = 0, ref = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * hz * i / FS;
            re += out[i] * Math.sin(w);
            im += out[i] * Math.cos(w);
            ref += Math.sin(w) * Math.sin(w);
        }
        double a = re / ref, b = im / ref;
        double resid = 0, fund = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * hz * i / FS;
            double f = a * Math.sin(w) + b * Math.cos(w);
            fund += f * f;
            resid += (out[i] - f) * (out[i] - f);
        }
        return Math.sqrt(resid / Math.max(1e-12, fund));
    }

    private static double[] run(double[] in) {
        Limiter lim = new Limiter();
        lim.setup(FS);
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = Limiter.ceiling(in[i] * lim.gainFor(in[i]));
        }
        return out;
    }

    private static double peak(double[] x) {
        double p = 0;
        for (double v : x) {
            p = Math.max(p, Math.abs(v));
        }
        return p;
    }

    @Test
    void aMasteredTrackPassesThroughUntouched() {
        // The fault this replaced: the old soft clipper started bending at 0.7, so ordinary
        // loud music was reshaped continuously — 5% THD with every knob at centre, heard as a
        // permanent electrical buzz. Real music sits at 0.9-0.98 constantly, and at those levels
        // the output stage must be transparent, not merely bounded.
        for (double amp : new double[]{0.5, 0.7, 0.8, 0.9, 0.95}) {
            double[] in = sine(1000, amp, (int) FS);
            double[] out = run(in);
            double distortion = thd(out, 1000);
            assertTrue(distortion < 0.001,
                    "a peak of " + amp + " was distorted by " + (100 * distortion) + "%");
            assertEquals(amp, peak(out), 0.01,
                    "a peak of " + amp + " should come out at the same level");
        }
    }

    @Test
    void nothingEverLeavesAboveFullScale() {
        for (double amp : new double[]{1.0, 1.5, 2.0, 4.0, 20.0}) {
            double[] out = run(sine(220, amp, (int) FS));
            assertTrue(peak(out) <= 1.0, "peak " + amp + " came out at " + peak(out));
        }
    }

    @Test
    void anOverdrivenSignalIsTurnedDownRatherThanDistorted() {
        // Turning the level down adds no harmonics; squashing the waveform does. This is the
        // whole reason for using a limiter instead of a clipper, so measure the difference.
        double[] out = run(sine(220, 2.0, (int) FS));
        double distortion = thd(out, 220);
        assertTrue(distortion < 0.05,
                "6 dB over should duck, not buzz; distortion was " + (100 * distortion) + "%");
    }

    @Test
    void itLetsGoAgainAfterAPeak() {
        // A limiter that never recovers is a fader stuck down: one loud moment and the rest of
        // the track plays quiet.
        Limiter lim = new Limiter();
        lim.setup(FS);
        for (int i = 0; i < FS / 10; i++) {
            lim.gainFor(3.0); // hammer it for 100 ms
        }
        double squashed = lim.gainFor(3.0);
        assertTrue(squashed < 0.5, "should be well into reduction, was " + squashed);
        for (int i = 0; i < FS; i++) {
            lim.gainFor(0.2); // then a second of quiet
        }
        assertTrue(lim.gainFor(0.2) > 0.99,
                "should have returned to unity, was " + lim.gainFor(0.2));
    }

    @Test
    void theCeilingNeverFlattensTheWave() {
        // A flat top is a square edge, and a square edge is broadband harmonics.
        double prev = Limiter.ceiling(1.0);
        for (double x = 1.01; x <= 8.0; x += 0.01) {
            double y = Limiter.ceiling(x);
            assertTrue(y > prev, "the ceiling flattened out at " + x);
            prev = y;
        }
    }

    @Test
    void theGuardSitsAboveWhatTheLimiterAimsFor() {
        // A guard that starts below the limiter's own target reshapes every peak the limiter
        // delivered correctly — it measured 0.9% distortion at every frequency, permanently.
        // Audio at the limiter's target level must pass the guard untouched.
        assertEquals(Limiter.THRESHOLD, Limiter.ceiling(Limiter.THRESHOLD), 0.0,
                "the guard is bending audio the limiter had already handled");
        for (double hz : new double[]{50, 80, 110, 220, 1000}) {
            double[] out = run(sine(hz, 1.0, (int) FS));
            assertTrue(thd(out, hz) < 0.002,
                    "a full-scale " + hz + " Hz tone was distorted by " + (100 * thd(out, hz)) + "%");
        }
    }

    @Test
    void theCeilingIsSymmetricAndLeavesNormalAudioExact() {
        for (double x = 0; x <= 0.99; x += 0.001) {
            assertEquals(x, Limiter.ceiling(x), 1e-12, "coloured audio already inside range");
            assertEquals(-Limiter.ceiling(x), Limiter.ceiling(-x), 1e-12,
                    "asymmetry would add even harmonics");
        }
    }
}
