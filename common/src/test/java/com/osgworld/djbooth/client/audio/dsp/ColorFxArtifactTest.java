package com.osgworld.djbooth.client.audio.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osgworld.djbooth.mixer.ColorFxModes;

import org.junit.jupiter.api.Test;

/**
 * Artefact tests for the SOUND COLOR FX stage: the same three questions asked of the EQ, since the
 * COLOR knob is a knob like any other and this stage bakes filter coefficients from it too.
 *
 * <ol>
 *   <li>Does turning the knob click?</li>
 *   <li>Can any mode run away in level?</li>
 *   <li>Does any mode go non-finite, at any knob position?</li>
 * </ol>
 */
class ColorFxArtifactTest {
    private static final int FS = 48000;
    private static final float WHEEL_NOTCH = 0.04f;
    private static final int FRAMES_PER_UI_FRAME = 800;

    private static final int[] ALL_MODES = {
        ColorFxModes.FILTER, ColorFxModes.SPACE, ColorFxModes.DUB_ECHO,
        ColorFxModes.SWEEP, ColorFxModes.NOISE, ColorFxModes.CRUSH,
    };

    private static double sine(int n, double f) {
        return Math.sin(2 * Math.PI * f * n / FS);
    }

    private static double maxCurvature(double[] y) {
        double worst = 0;
        for (int i = 2; i < y.length; i++) {
            worst = Math.max(worst, Math.abs(y[i] - 2 * y[i - 1] + y[i - 2]));
        }
        return worst;
    }

    private static double naturalCurvature(double freqHz, double amp) {
        double[] tone = new double[FS / 10];
        for (int n = 0; n < tone.length; n++) {
            tone[n] = sine(n, freqHz) * amp;
        }
        return maxCurvature(tone);
    }

    private static ColorFx fxAt(int mode, double knob, double param) {
        ColorFx fx = new ColorFx();
        fx.setup(FS);
        fx.set(mode, knob, param);
        return fx;
    }

    /**
     * Sweep the COLOR knob from centre out to one end in wheel notches and report the sharpest bend
     * in the output, relative to what the test tone bends by on its own.
     */
    private static double sweepCurvatureRatio(int mode, boolean rightward, double freqHz) {
        double amp = 0.5;
        ColorFx fx = fxAt(mode, 0.5, 0.5);
        int frames = FS / 2;
        double[] out = new double[frames];
        double knob = 0.5;
        for (int n = 0; n < frames; n++) {
            if (n % FRAMES_PER_UI_FRAME == 0) {
                knob = rightward ? Math.min(1.0, knob + WHEEL_NOTCH) : Math.max(0.0, knob - WHEEL_NOTCH);
                fx.set(mode, knob, 0.5);
            }
            out[n] = fx.process(sine(n, freqHz) * amp);
        }
        return maxCurvature(out) / naturalCurvature(freqHz, amp);
    }

    @Test
    void centreDetentIsFullyDryInEveryMode() {
        for (int mode : ALL_MODES) {
            ColorFx fx = fxAt(mode, 0.5, 0.5);
            for (int n = 0; n < 4096; n++) {
                double in = sine(n, 440) * 0.5;
                assertEquals(in, fx.process(in), 1e-12,
                        "mode " + mode + " coloured the signal at the centre detent");
            }
        }
    }

    @Test
    void sweepingTheColourKnobDoesNotClick() {
        // The FILTER mode is the one that sweeps a cutoff across the audible range, so it is where
        // a coefficient jump shows most. 440 Hz sits inside the sweep on both sides.
        double worst = 0;
        String where = "";
        for (boolean right : new boolean[] {false, true}) {
            double ratio = sweepCurvatureRatio(ColorFxModes.FILTER, right, 440);
            if (ratio > worst) {
                worst = ratio;
                where = right ? "turning right (high-pass)" : "turning left (low-pass)";
            }
        }
        assertTrue(worst < 3.0,
                "sweeping COLOR " + where + " bent the waveform " + worst
                        + "x more than the tone does on its own: the filter is clicking");
    }

    @Test
    void noModeRunsAwayInLevel() {
        // Every mode, both sides, every PARAMETER position, fed a full-scale tone. A feedback path
        // that is not scaled for its own gain shows up here as a level that keeps climbing.
        for (int mode : ALL_MODES) {
            for (double knob : new double[] {0.0, 0.15, 0.35, 0.65, 0.85, 1.0}) {
                for (double param : new double[] {0.0, 0.5, 1.0}) {
                    ColorFx fx = fxAt(mode, knob, param);
                    double peak = 0;
                    for (int n = 0; n < FS * 4; n++) { // four seconds: long enough for a tail to build
                        peak = Math.max(peak, Math.abs(fx.process(sine(n, 220))));
                    }
                    assertTrue(peak < 4.0,
                            "mode " + mode + " knob=" + knob + " param=" + param
                                    + " reached " + peak + " from a full-scale input");
                }
            }
        }
    }

    @Test
    void noModeGoesNonFinite() {
        java.util.Random rng = new java.util.Random(11);
        for (int mode : ALL_MODES) {
            for (double knob = 0.0; knob <= 1.0001; knob += 0.1) {
                ColorFx fx = fxAt(mode, knob, 1.0);
                for (int n = 0; n < 8192; n++) {
                    double y = fx.process(rng.nextDouble() * 2 - 1);
                    assertTrue(Double.isFinite(y),
                            "mode " + mode + " went non-finite at knob=" + knob);
                }
            }
        }
    }

    @Test
    void resetSilencesEveryTail() {
        // A seek must not let the old track's reverb or echo ring into the new position.
        for (int mode : new int[] {ColorFxModes.SPACE, ColorFxModes.DUB_ECHO}) {
            ColorFx fx = fxAt(mode, 0.9, 1.0);
            for (int n = 0; n < FS; n++) {
                fx.process(sine(n, 220));
            }
            fx.reset();
            double worst = 0;
            for (int n = 0; n < FS; n++) {
                worst = Math.max(worst, Math.abs(fx.process(0.0)));
            }
            assertTrue(worst < 1e-6,
                    "mode " + mode + " kept ringing after reset, peaking at " + worst);
        }
    }
}
