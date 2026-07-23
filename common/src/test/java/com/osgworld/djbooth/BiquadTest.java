package com.osgworld.djbooth;

import com.osgworld.djbooth.client.audio.dsp.Biquad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frequency-response checks for the DSP biquads. We drive each filter with a pure sine and measure
 * the steady-state output/input amplitude ratio, so these assert the EQ/filter actually do what the
 * mixer claims (boost, kill, low-pass, high-pass) rather than just that the code runs.
 */
class BiquadTest {
    private static final double FS = 48000.0;

    /** Steady-state gain (linear) of a filter at a given frequency, measured by RMS so the result
     *  is independent of where the samples land on the sine (peak-sampling under-reads high freqs). */
    private static double gainAt(Biquad bq, double freq) {
        int n = 8192;
        double w = 2 * Math.PI * freq / FS;
        double sumIn = 0, sumOut = 0;
        for (int i = 0; i < n; i++) {
            double x = Math.sin(w * i);
            double y = bq.process(x);
            if (i > n / 2) {
                sumIn += x * x;
                sumOut += y * y;
            }
        }
        return Math.sqrt(sumOut / sumIn);
    }

    private static double db(double gain) {
        return 20 * Math.log10(gain);
    }

    @Test
    void lowShelfBoostsBassLeavesTrebleAlone() {
        Biquad bq = new Biquad();
        bq.lowShelf(FS, 200, 6);          // +6 dB low shelf
        assertEquals(6.0, db(gainAt(bq, 40)), 1.0);   // deep bass ~ +6 dB
        Biquad hi = new Biquad();
        hi.lowShelf(FS, 200, 6);
        assertEquals(0.0, db(gainAt(hi, 8000)), 1.0); // treble untouched
    }

    @Test
    void lowShelfKillRemovesBass() {
        Biquad bq = new Biquad();
        bq.lowShelf(FS, 200, -60);        // isolator kill
        assertTrue(db(gainAt(bq, 40)) < -30, "bass should be crushed");
        Biquad hi = new Biquad();
        hi.lowShelf(FS, 200, -60);
        assertEquals(0.0, db(gainAt(hi, 10000)), 1.5); // highs still pass
    }

    @Test
    void highShelfBoostsTreble() {
        Biquad bq = new Biquad();
        bq.highShelf(FS, 4000, 6);
        assertEquals(6.0, db(gainAt(bq, 15000)), 1.0);
        Biquad lo = new Biquad();
        lo.highShelf(FS, 4000, 6);
        assertEquals(0.0, db(gainAt(lo, 100)), 1.0);
    }

    @Test
    void lowpassKillsHighsPassesLows() {
        Biquad bq = new Biquad();
        bq.lowpass(FS, 500, 2.0);
        assertTrue(gainAt(bq, 100) > 0.8, "lows pass");
        Biquad hi = new Biquad();
        hi.lowpass(FS, 500, 2.0);
        assertTrue(db(gainAt(hi, 12000)) < -20, "highs cut");
    }

    @Test
    void highpassKillsLowsPassesHighs() {
        Biquad bq = new Biquad();
        bq.highpass(FS, 2000, 2.0);
        assertTrue(gainAt(bq, 12000) > 0.8, "highs pass");
        Biquad lo = new Biquad();
        lo.highpass(FS, 2000, 2.0);
        assertTrue(db(gainAt(lo, 80)) < -20, "lows cut");
    }

    @Test
    void identityIsTransparent() {
        Biquad bq = new Biquad();
        bq.identity();
        assertEquals(1.0, gainAt(bq, 1000), 0.02);
    }

    @Test
    void peakingBoostsCentreNotEdges() {
        Biquad bq = new Biquad();
        bq.peaking(FS, 1000, 6, 0.9);
        assertEquals(6.0, db(gainAt(bq, 1000)), 1.0);
        Biquad lo = new Biquad();
        lo.peaking(FS, 1000, 6, 0.9);
        assertTrue(db(gainAt(lo, 40)) < 3, "far-off bass barely boosted");
    }
}
