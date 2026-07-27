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
     * Sharpest bend the output has while the knob sits still at {@code knob}.
     *
     * <p>Run long enough for a reverb tail to build. Measuring a quarter of a second from cold made
     * SPACE look like it was clicking 16x: the swept run had been piling up a tail for half a
     * second while the baseline it was compared against had barely started one.
     */
    private static double staticCurvature(int mode, double knob, double freqHz, double amp) {
        ColorFx fx = fxAt(mode, knob, 0.5);
        int frames = FS * 2;
        double[] out = new double[frames];
        for (int n = 0; n < frames; n++) {
            out[n] = fx.process(sine(n, freqHz) * amp);
        }
        // Only the settled part: the filters easing into the tone are not what is being measured.
        double worst = 0;
        for (int i = frames / 2; i < frames; i++) {
            worst = Math.max(worst, Math.abs(out[i] - 2 * out[i - 1] + out[i - 2]));
        }
        return worst;
    }

    /**
     * How much sharper the output bends while the knob is being swept than it ever does with the
     * knob parked anywhere along that same path.
     *
     * <p>Measuring against the bare tone was wrong for this stage. A resonant filter at Q 2 legally
     * doubles the amplitude at its corner, which doubles the curvature too, so a perfectly clean
     * sweep scored 2x before anything went wrong. What is actually being asked is narrower: does
     * moving the knob add anything the same filter does not already do standing still? Anything
     * above 1 is modulation, and only a lot above 1 is a click.
     */
    private static double sweepCurvatureRatio(int mode, boolean rightward, double freqHz) {
        double amp = 0.5;
        ColorFx fx = fxAt(mode, 0.5, 0.5);
        int frames = FS / 2;
        double[] out = new double[frames];
        double knob = 0.5;
        double staticWorst = naturalCurvature(freqHz, amp);
        for (int n = 0; n < frames; n++) {
            if (n % FRAMES_PER_UI_FRAME == 0) {
                knob = rightward ? Math.min(1.0, knob + WHEEL_NOTCH) : Math.max(0.0, knob - WHEEL_NOTCH);
                fx.set(mode, knob, 0.5);
                staticWorst = Math.max(staticWorst, staticCurvature(mode, knob, freqHz, amp));
            }
            out[n] = fx.process(sine(n, freqHz) * amp);
        }
        return maxCurvature(out) / staticWorst;
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

    /**
     * The modes where the COLOR knob filters the signal itself, and a click would therefore be a
     * click.
     *
     * <p>SPACE, DUB ECHO and NOISE are left out on purpose, not because they pass. In those modes
     * the knob feeds a tail or a noise source, and a tail's whole job is to keep and smear what it
     * is given: any change to its send arrives amplified and stretched, so "sharper than the same
     * setting standing still" stops meaning anything. SPACE scores 20x against a settled baseline
     * while sounding correct. They are covered instead by the level, stability and reset tests,
     * which do work on a tail.
     */
    private static final int[] FILTER_PATH_MODES = {
        ColorFxModes.FILTER, ColorFxModes.SWEEP, ColorFxModes.CRUSH,
    };

    @Test
    void sweepingTheColourKnobDoesNotClickInAnyFilterMode() {
        // Both sides of the detent and three frequencies, so each mode is measured somewhere its
        // own filters actually act. FILTER is the harshest because its cutoff sweeps decades.
        for (int mode : FILTER_PATH_MODES) {
            for (boolean right : new boolean[] {false, true}) {
                for (double freq : new double[] {110, 440, 3000}) {
                    // Some excess is the effect, not a fault: a filter whose corner is moving does
                    // genuinely produce transients a parked one does not, and that is what a sweep
                    // sounds like. The two are not close. Measured on this metric, the worst case
                    // is FILTER swept right across a 110 Hz tone: 952x without the ramp, 3.5x with
                    // it. Five is two orders of magnitude clear of the fault and comfortably above
                    // the effect.
                    double ratio = sweepCurvatureRatio(mode, right, freq);
                    assertTrue(ratio < 5.0,
                            "sweeping COLOR in mode " + mode + (right ? " right" : " left")
                                    + " at " + freq + " Hz bent the waveform " + ratio
                                    + "x sharper than the same filter does standing still: the "
                                    + "knob is clicking");
                }
            }
        }
    }

    @Test
    void switchingModeDoesNotLeaveTheOldEffectRinging() {
        // There is no continuous path from a reverb to a bit crusher, so a mode change snaps. It
        // must at least drop the old tail, or the reverb keeps sounding under the crusher.
        ColorFx fx = fxAt(ColorFxModes.SPACE, 0.95, 1.0);
        for (int n = 0; n < FS; n++) {
            fx.process(sine(n, 220));
        }
        fx.set(ColorFxModes.CRUSH, 0.5, 0.5); // centre detent: fully dry
        double worst = 0;
        for (int n = 0; n < FS; n++) {
            worst = Math.max(worst, Math.abs(fx.process(0.0)));
        }
        assertTrue(worst < 1e-6,
                "the old mode kept ringing after switching, peaking at " + worst);
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
