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

    // --- Behaviour the DJM-900NXS2 manual specifies for SWEEP and DUB ECHO -------------------------

    private static double[] sine(double hz, double amp, int n) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = amp * Math.sin(2 * Math.PI * hz * i / FS);
        }
        return out;
    }

    /** RMS of the second half of the run, after any ramp has settled. */
    private static double settledRms(ColorFx fx, double[] in) {
        double sum = 0;
        int from = in.length / 2;
        for (int i = 0; i < in.length; i++) {
            double y = fx.process(in[i]);
            if (i >= from) {
                sum += y * y;
            }
        }
        return Math.sqrt(sum / (in.length - from));
    }

    @Test
    void sweepRightPutsTheBandPassWhereParameterSaysAndOnlyThere() {
        // Manual: with COLOR turned right, PARAMETER sets the band pass's centre frequency, and
        // turning it right raises it. At full COLOR the sound is all band pass, so a tone sitting
        // on the centre goes through and one far away does not.
        double low = settledRms(stage(ColorFxModes.SWEEP, 1.0, 0.0), sine(200, 0.5, 24000));
        double lowAway = settledRms(stage(ColorFxModes.SWEEP, 1.0, 0.0), sine(5000, 0.5, 24000));
        assertTrue(low > 0.25, "a 200 Hz tone should pass with the centre at its lowest: " + low);
        assertTrue(lowAway < 0.05, "a 5 kHz tone should not: " + lowAway);

        double high = settledRms(stage(ColorFxModes.SWEEP, 1.0, 1.0), sine(8000, 0.5, 24000));
        double highAway = settledRms(stage(ColorFxModes.SWEEP, 1.0, 1.0), sine(200, 0.5, 24000));
        assertTrue(high > 0.25, "an 8 kHz tone should pass with the centre at its highest: " + high);
        assertTrue(highAway < 0.05, "a 200 Hz tone should not: " + highAway);
    }

    @Test
    void sweepRightCentreRisesMonotonicallyWithParameter() {
        // Probe with a fixed 1.5 kHz tone: the closer the centre gets to it the more comes through.
        // It must rise up to the parameter where the centre crosses 1.5 kHz, then fall again.
        double best = -1;
        int bestAt = -1;
        double[] levels = new double[11];
        for (int i = 0; i <= 10; i++) {
            levels[i] = settledRms(stage(ColorFxModes.SWEEP, 1.0, i / 10.0), sine(1500, 0.5, 24000));
            if (levels[i] > best) {
                best = levels[i];
                bestAt = i;
            }
        }
        // 200 * 40^p = 1500 Hz  =>  p ~ 0.55.
        assertTrue(bestAt >= 4 && bestAt <= 7,
                "the centre should cross 1.5 kHz near the middle of PARAMETER, was " + bestAt);
        for (int i = 1; i <= bestAt; i++) {
            assertTrue(levels[i] >= levels[i - 1] - 1e-3, "rising side not monotonic at " + i);
        }
        for (int i = bestAt + 1; i <= 10; i++) {
            assertTrue(levels[i] <= levels[i - 1] + 1e-3, "falling side not monotonic at " + i);
        }
    }

    @Test
    void sweepLeftGateClosesHarderAsParameterGoesUp() {
        // Manual: with COLOR turned left PARAMETER sets the gate; turn it right to limit the sound.
        double[] quiet = sine(440, 0.2, 24000);
        double soft = settledRms(stage(ColorFxModes.SWEEP, 0.0, 0.0), quiet);
        double hard = settledRms(stage(ColorFxModes.SWEEP, 0.0, 1.0), quiet);
        assertTrue(hard < soft * 0.5,
                "a harder gate should take more out of a quiet signal: " + soft + " vs " + hard);
        // And a loud signal is barely touched whatever PARAMETER is: only its zero crossings.
        double[] loud = sine(440, 0.9, 24000);
        double loudSoft = settledRms(stage(ColorFxModes.SWEEP, 0.0, 0.0), loud);
        double loudHard = settledRms(stage(ColorFxModes.SWEEP, 0.0, 1.0), loud);
        assertTrue(loudHard > loudSoft * 0.9, "a loud signal should survive the hardest gate");
    }

    /** Energy in the tail of a DUB ECHO fed a tone and then silence, so only the repeats are left. */
    private static double dubEchoTail(double knob, double toneHz) {
        ColorFx fx = stage(ColorFxModes.DUB_ECHO, knob, 1.0);
        for (double s : sine(toneHz, 0.5, 20000)) {
            fx.process(s);
        }
        double tail = 0;
        for (int i = 0; i < 40000; i++) {
            tail += Math.abs(fx.process(0.0));
        }
        return tail;
    }

    @Test
    void dubEchoLeftRepeatsOnlyTheMidsAndRightOnlyTheHighs() {
        // Manual: COLOR left applies the echo to the mids only, COLOR right to the highs only.
        double leftMid = dubEchoTail(0.0, 1000);
        double leftBass = dubEchoTail(0.0, 60);
        double leftHigh = dubEchoTail(0.0, 9000);
        assertTrue(leftMid > 4 * leftBass, "left should leave the bass out of the echo");
        assertTrue(leftMid > 4 * leftHigh, "left should leave the highs out of the echo");

        double rightHigh = dubEchoTail(1.0, 9000);
        double rightMid = dubEchoTail(1.0, 1000);
        double rightBass = dubEchoTail(1.0, 60);
        assertTrue(rightHigh > 4 * rightMid, "right should leave the mids out of the echo");
        assertTrue(rightHigh > 20 * rightBass, "right should leave the bass out of the echo");
    }
}
