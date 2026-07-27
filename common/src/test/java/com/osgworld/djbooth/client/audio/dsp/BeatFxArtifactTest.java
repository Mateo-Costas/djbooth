package com.osgworld.djbooth.client.audio.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osgworld.djbooth.mixer.BeatFxTypes;

import org.junit.jupiter.api.Test;

/**
 * Artefact tests for the BEAT FX stage, run across all fourteen effects rather than the handful
 * anyone thinks to try by hand.
 *
 * <p>Most of these effects are feedback loops or resamplers. Both fail in ways that are obvious in
 * numbers and easy to miss by ear until the one night the level climbs: a delay whose feedback is
 * not scaled for its own gain, a read pointer that walks off its buffer, a filter that goes
 * unstable at a corner of the beat range.
 */
class BeatFxArtifactTest {
    private static final int FS = 48000;

    private static double sine(int n, double f) {
        return Math.sin(2 * Math.PI * f * n / FS);
    }

    private static BeatFx fxAt(int type, double seconds, double depth, int bands, boolean right) {
        BeatFx fx = new BeatFx(right);
        fx.setup(FS);
        fx.set(type, true, seconds, depth, bands);
        return fx;
    }

    /** Every effect time the panel can ask for: slowest and fastest tempo, narrowest and widest beat. */
    private static double[] panelTimes() {
        double[] out = new double[BeatFxTypes.BEATS.length * 2];
        int i = 0;
        for (double beats : BeatFxTypes.BEATS) {
            out[i++] = beats * 60.0 / 40.0;  // 40 BPM, the slowest the mixer accepts
            out[i++] = beats * 60.0 / 200.0; // 200 BPM, the fastest
        }
        return out;
    }

    @Test
    void everyEffectLeavesTheSignalAloneWhenOff() {
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            BeatFx fx = new BeatFx(false);
            fx.setup(FS);
            fx.set(type, false, 0.5, 1.0, BeatFxTypes.BANDS_ALL);
            for (int n = 0; n < 8192; n++) {
                double in = sine(n, 440) * 0.5;
                assertEquals(in, fx.process(in), 1e-9,
                        "effect " + BeatFxTypes.NAMES[type] + " altered the signal while switched off");
            }
        }
    }

    @Test
    void everyEffectLeavesTheSignalAloneAtZeroDepth() {
        // The manual is explicit: LEVEL/DEPTH fully down is dry, whatever the effect.
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            BeatFx fx = fxAt(type, 0.5, 0.0, BeatFxTypes.BANDS_ALL, false);
            for (int n = 0; n < 8192; n++) {
                double in = sine(n, 440) * 0.5;
                assertEquals(in, fx.process(in), 1e-9,
                        "effect " + BeatFxTypes.NAMES[type] + " was audible at zero depth");
            }
        }
    }

    @Test
    void noEffectRunsAwayInLevel() {
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            for (double t : panelTimes()) {
                for (boolean right : new boolean[] {false, true}) {
                    BeatFx fx = fxAt(type, t, 1.0, BeatFxTypes.BANDS_ALL, right);
                    double peak = 0;
                    for (int n = 0; n < FS * 4; n++) {
                        peak = Math.max(peak, Math.abs(fx.process(sine(n, 220))));
                    }
                    assertTrue(peak < 4.0,
                            BeatFxTypes.NAMES[type] + " at " + t + "s reached " + peak
                                    + " from a full-scale input");
                }
            }
        }
    }

    @Test
    void noEffectGoesNonFinite() {
        java.util.Random rng = new java.util.Random(23);
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            for (double t : panelTimes()) {
                BeatFx fx = fxAt(type, t, 1.0, BeatFxTypes.BANDS_ALL, false);
                for (int n = 0; n < 16384; n++) {
                    double y = fx.process(rng.nextDouble() * 2 - 1);
                    assertTrue(Double.isFinite(y),
                            BeatFxTypes.NAMES[type] + " went non-finite at " + t + "s");
                }
            }
        }
    }

    @Test
    void everyBandCombinationIsStable() {
        // FX FREQUENCY can mute any subset of the three bands, including all of them.
        for (int bands = 0; bands <= BeatFxTypes.BANDS_ALL; bands++) {
            for (int type = 0; type < BeatFxTypes.TYPES; type++) {
                BeatFx fx = fxAt(type, 0.5, 1.0, bands, false);
                double peak = 0;
                for (int n = 0; n < FS; n++) {
                    double y = fx.process(sine(n, 220));
                    assertTrue(Double.isFinite(y),
                            BeatFxTypes.NAMES[type] + " went non-finite with bands=" + bands);
                    peak = Math.max(peak, Math.abs(y));
                }
                assertTrue(peak < 4.0,
                        BeatFxTypes.NAMES[type] + " reached " + peak + " with bands=" + bands);
            }
        }
    }

    @Test
    void resetSilencesEveryTail() {
        // A seek must not let the old position's delay tail ring into the new one.
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            BeatFx fx = fxAt(type, 0.5, 1.0, BeatFxTypes.BANDS_ALL, false);
            for (int n = 0; n < FS; n++) {
                fx.process(sine(n, 220));
            }
            fx.reset();
            double worst = 0;
            for (int n = 0; n < FS * 2; n++) {
                worst = Math.max(worst, Math.abs(fx.process(0.0)));
            }
            assertTrue(worst < 1e-6,
                    BeatFxTypes.NAMES[type] + " kept ringing after reset, peaking at " + worst);
        }
    }

    @Test
    void retimingDoesNotRestartTheEffect() {
        // Changing the beat fraction retimes the effect rather than restarting it, so the tail has
        // to survive the change rather than being dropped.
        BeatFx fx = fxAt(BeatFxTypes.DELAY, 0.5, 1.0, BeatFxTypes.BANDS_ALL, false);
        for (int n = 0; n < FS; n++) {
            fx.process(sine(n, 220));
        }
        fx.set(BeatFxTypes.DELAY, true, 0.25, 1.0, BeatFxTypes.BANDS_ALL);
        double tail = 0;
        for (int n = 0; n < FS / 2; n++) {
            tail = Math.max(tail, Math.abs(fx.process(0.0)));
        }
        assertTrue(tail > 1e-3, "retiming DELAY wiped the tail instead of retiming it");
    }
}
