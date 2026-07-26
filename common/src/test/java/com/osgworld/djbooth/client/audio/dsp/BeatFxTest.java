package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.BeatFxTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The BEAT FX stage runs on the audio thread across fourteen effects; none may misbehave. */
class BeatFxTest {

    private static final double FS = 48000;

    private static BeatFx stage(int type, boolean on, double seconds, double depth) {
        BeatFx fx = new BeatFx(false);
        fx.setup(FS);
        fx.set(type, on, seconds, depth, BeatFxTypes.BANDS_ALL);
        return fx;
    }

    private static double[] tone(int samples) {
        double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = Math.sin(2 * Math.PI * 440.0 * i / FS) * 0.5;
        }
        return out;
    }

    @Test
    void switchedOffPassesAudioThroughUntouched() {
        double[] in = tone(4000);
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            BeatFx fx = stage(type, false, 0.5, 1.0);
            for (double s : in) {
                assertEquals(s, fx.process(s), 1e-12,
                        BeatFxTypes.NAMES[type] + " should be dry while switched off");
            }
        }
    }

    @Test
    void depthAtZeroIsAlsoDry() {
        double[] in = tone(4000);
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            BeatFx fx = stage(type, true, 0.5, 0.0);
            for (double s : in) {
                assertEquals(s, fx.process(s), 1e-12,
                        BeatFxTypes.NAMES[type] + " should be dry with LEVEL/DEPTH down");
            }
        }
    }

    @Test
    void everyEffectStaysFiniteAndBoundedAtFullDepth() {
        double[] in = tone(60000); // long enough for the beat-length loops to wrap several times
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            for (double seconds : new double[]{0.02, 0.25, 2.0}) {
                BeatFx fx = stage(type, true, seconds, 1.0);
                for (double s : in) {
                    double y = fx.process(s);
                    assertTrue(Double.isFinite(y),
                            BeatFxTypes.NAMES[type] + " @" + seconds + "s went non-finite");
                    assertTrue(Math.abs(y) < 12.0,
                            BeatFxTypes.NAMES[type] + " @" + seconds + "s ran away: " + y);
                }
            }
        }
    }

    @Test
    void delayRepeatsAfterTheEffectTime() {
        double seconds = 0.1;
        int delaySamples = (int) (seconds * FS);
        BeatFx fx = stage(BeatFxTypes.DELAY, true, seconds, 1.0);
        // One loud sample, then silence: the repeat should show up an effect time later.
        fx.process(1.0);
        double peak = 0;
        int peakAt = -1;
        for (int i = 1; i < delaySamples * 2; i++) {
            double y = Math.abs(fx.process(0.0));
            if (y > peak) {
                peak = y;
                peakAt = i;
            }
        }
        assertTrue(peak > 0.1, "the delayed repeat should be audible");
        assertTrue(Math.abs(peakAt - delaySamples) < 8,
                "repeat landed at " + peakAt + ", expected near " + delaySamples);
    }

    @Test
    void vinylBrakeRunsDownToSilence() {
        BeatFx fx = stage(BeatFxTypes.VINYL_BRAKE, true, 0.5, 1.0);
        double[] in = tone(200000);
        double last = 0;
        for (double s : in) {
            last = fx.process(s);
        }
        // Once the "platter" has stopped the read pointer is frozen, so the output settles.
        double settled = 0;
        for (int i = 0; i < 2000; i++) {
            settled += Math.abs(fx.process(0.0) - last);
        }
        assertTrue(settled / 2000 < 0.05, "the brake should come to a stop");
    }

    @Test
    void mutingEveryFxFrequencyBandLeavesTheSignalDry() {
        BeatFx fx = new BeatFx(false);
        fx.setup(FS);
        fx.set(BeatFxTypes.ECHO, true, 0.1, 1.0, 0); // no bands sent into the effect
        for (double s : tone(4000)) {
            assertEquals(s, fx.process(s), 1e-9,
                    "with no bands enabled the effect should not be heard");
        }
    }
}
