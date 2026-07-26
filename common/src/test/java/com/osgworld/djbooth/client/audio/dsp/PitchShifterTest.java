package com.osgworld.djbooth.client.audio.dsp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MASTER TEMPO's pitch shifter: it has to be transparent when off and in tune when on. */
class PitchShifterTest {

    private static final double FS = 48000;

    private static double[] tone(double hz, int samples) {
        double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = Math.sin(2 * Math.PI * hz * i / FS) * 0.5;
        }
        return out;
    }

    private static PitchShifter shifter(double ratio) {
        PitchShifter p = new PitchShifter();
        p.setup(FS);
        p.setRatio(ratio);
        return p;
    }

    /**
     * Estimate the dominant frequency by counting zero crossings, which is plenty to tell a
     * shifted tone from an unshifted one and needs no FFT.
     */
    private static double dominantHz(double[] signal) {
        int crossings = 0;
        for (int i = 1; i < signal.length; i++) {
            if (signal[i - 1] <= 0 && signal[i] > 0) {
                crossings++;
            }
        }
        return crossings / (signal.length / FS);
    }

    private static double[] run(PitchShifter p, double[] in) {
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = p.process(in[i]);
        }
        // Drop the first window: the buffer starts empty, so the head is fade-in, not signal.
        return java.util.Arrays.copyOfRange(out, (int) (FS * 0.15), out.length);
    }

    @Test
    void unityRatioIsBitExactlyTransparent() {
        PitchShifter p = shifter(1.0);
        assertTrue(p.isBypassed());
        for (double s : tone(440, 4000)) {
            assertEquals(s, p.process(s), 0.0, "an unshifted deck must not be touched at all");
        }
    }

    @Test
    void shiftingDownLowersThePitchByTheRatio() {
        // A deck pulled 16% fast, corrected back: the ratio MASTER TEMPO would ask for.
        double ratio = 1.0 / 1.16;
        double[] out = run(shifter(ratio), tone(1000, (int) (FS * 2)));
        double hz = dominantHz(out);
        assertEquals(1000 * ratio, hz, 1000 * ratio * 0.06,
                "a 1 kHz tone shifted by " + ratio + " should land near " + (1000 * ratio));
    }

    @Test
    void shiftingUpRaisesThePitchByTheRatio() {
        double ratio = 1.10;
        double[] out = run(shifter(ratio), tone(1000, (int) (FS * 2)));
        assertEquals(1000 * ratio, dominantHz(out), 1000 * ratio * 0.06);
    }

    @Test
    void staysBoundedAndFiniteAcrossTheTempoFadersRange() {
        // WIDE is the widest a tempo fader goes, so this is the whole range MASTER TEMPO must cover.
        for (double rate : new double[]{0.4, 0.84, 0.94, 1.06, 1.16, 1.6}) {
            PitchShifter p = shifter(1.0 / rate);
            for (double s : tone(440, (int) (FS * 0.5))) {
                double y = p.process(s);
                assertTrue(Double.isFinite(y), "rate " + rate + " went non-finite");
                assertTrue(Math.abs(y) < 2.0, "rate " + rate + " ran away: " + y);
            }
        }
    }

    @Test
    void outputKeepsRoughlyTheSameLevel() {
        // The crossfade is meant to be constant power; a shifted track shouldn't duck or jump.
        double[] in = tone(440, (int) (FS * 2));
        double[] out = run(shifter(1.0 / 1.16), in);
        double rmsIn = rms(in);
        double rmsOut = rms(out);
        assertTrue(rmsOut > rmsIn * 0.6 && rmsOut < rmsIn * 1.4,
                "level moved too much: " + rmsIn + " -> " + rmsOut);
    }

    private static double rms(double[] xs) {
        double sum = 0;
        for (double x : xs) {
            sum += x * x;
        }
        return Math.sqrt(sum / xs.length);
    }
}
