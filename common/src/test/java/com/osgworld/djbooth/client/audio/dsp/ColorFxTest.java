package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.ColorFxModes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The COLOR stage has a centre detent and six modes; none of them may misbehave on the audio thread. */
class ColorFxTest {

    private static final double FS = 48000;

    private static ColorFx stage(int mode, double knob, double param) {
        ColorFx fx = new ColorFx();
        fx.setup(FS);
        fx.set(mode, knob, param);
        return fx;
    }

    /** A short burst of a 440 Hz tone, the signal every check below is fed. */
    private static double[] tone(int samples) {
        double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = Math.sin(2 * Math.PI * 440.0 * i / FS) * 0.5;
        }
        return out;
    }

    private static double rms(ColorFx fx, double[] in) {
        double sum = 0;
        for (double s : in) {
            double y = fx.process(s);
            sum += y * y;
        }
        return Math.sqrt(sum / in.length);
    }

    @Test
    void centreDetentPassesAudioThroughUntouched() {
        double[] in = tone(2000);
        for (int mode = 0; mode < ColorFxModes.MODES; mode++) {
            ColorFx fx = stage(mode, 0.5, 0.5);
            for (double s : in) {
                assertEquals(s, fx.process(s), 1e-12,
                        "mode " + ColorFxModes.NAMES[mode] + " should be dry at centre");
            }
        }
    }

    @Test
    void everyModeStaysFiniteAndBoundedWhenDrivenHard() {
        double[] in = tone(20000);
        for (int mode = 0; mode < ColorFxModes.MODES; mode++) {
            for (double knob : new double[]{0.0, 0.25, 0.75, 1.0}) {
                ColorFx fx = stage(mode, knob, 1.0); // parameter at maximum: longest tails
                for (double s : in) {
                    double y = fx.process(s);
                    assertTrue(Double.isFinite(y),
                            "mode " + ColorFxModes.NAMES[mode] + " knob " + knob + " went non-finite");
                    assertTrue(Math.abs(y) < 8.0,
                            "mode " + ColorFxModes.NAMES[mode] + " knob " + knob + " ran away: " + y);
                }
            }
        }
    }

    @Test
    void filterLeftCutsTrebleAndRightCutsBass() {
        int n = 8000;
        double[] treble = new double[n];
        double[] bass = new double[n];
        for (int i = 0; i < n; i++) {
            treble[i] = Math.sin(2 * Math.PI * 9000.0 * i / FS) * 0.5;
            bass[i] = Math.sin(2 * Math.PI * 60.0 * i / FS) * 0.5;
        }
        // Hard left: a low-pass, so the 9 kHz tone should all but vanish.
        assertTrue(rms(stage(ColorFxModes.FILTER, 0.0, 0.0), treble) < 0.05,
                "low-pass sweep should kill treble");
        // Hard right: a high-pass, so the 60 Hz tone should all but vanish.
        assertTrue(rms(stage(ColorFxModes.FILTER, 1.0, 0.0), bass) < 0.05,
                "high-pass sweep should kill bass");
    }

    @Test
    void noiseAddsSignalToSilence() {
        ColorFx fx = stage(ColorFxModes.NOISE, 1.0, 1.0);
        double sum = 0;
        for (int i = 0; i < 4000; i++) {
            double y = fx.process(0.0);
            sum += y * y;
        }
        assertTrue(Math.sqrt(sum / 4000) > 1e-3, "NOISE should mix noise into a silent input");
    }

    @Test
    void reverbAndEchoRingOnAfterTheInputStops() {
        // Long enough to fill the longest delay line (DUB ECHO repeats after 0.28 s) and then
        // listen past it, otherwise the "tail" is just the line's initial silence.
        for (int mode : new int[]{ColorFxModes.SPACE, ColorFxModes.DUB_ECHO}) {
            ColorFx fx = stage(mode, 0.0, 1.0);
            for (double s : tone(30000)) {
                fx.process(s);
            }
            double tail = 0;
            for (int i = 0; i < 30000; i++) {
                tail += Math.abs(fx.process(0.0));
            }
            assertNotEquals(0.0, tail, "mode " + ColorFxModes.NAMES[mode] + " should leave a tail");
        }
    }

    @Test
    void resetClearsTheTail() {
        ColorFx fx = stage(ColorFxModes.SPACE, 0.0, 1.0);
        for (double s : tone(4000)) {
            fx.process(s);
        }
        fx.reset();
        // Straight after a reset the tail is gone, so silence in is (near) silence out.
        double tail = 0;
        for (int i = 0; i < 500; i++) {
            tail += Math.abs(fx.process(0.0));
        }
        assertEquals(0.0, tail, 1e-9);
    }
}
