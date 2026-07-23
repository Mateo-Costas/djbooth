package com.osgworld.djbooth.client.audio.dsp;

/**
 * A single biquad IIR filter (RBJ "Audio EQ Cookbook" coefficients) in Direct Form II transposed.
 * One instance filters one channel of a mono stream; keep one per audio channel. Coefficients are
 * recomputed by the factory helpers whenever a knob moves; the {@code z1/z2} state carries between
 * blocks so the filter is continuous.
 */
public final class Biquad {
    // Normalized coefficients (a0 folded to 1).
    private double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    // Filter state (per channel).
    private double z1 = 0, z2 = 0;

    /** Pass-through (no colouring). */
    public void identity() {
        b0 = 1; b1 = 0; b2 = 0; a1 = 0; a2 = 0;
    }

    public void lowShelf(double fs, double f0, double dBgain) {
        double A = Math.pow(10, dBgain / 40.0);
        double w0 = 2 * Math.PI * f0 / fs;
        double cw = Math.cos(w0), sw = Math.sin(w0);
        double alpha = sw / 2.0 * Math.sqrt((A + 1 / A) * (1 / 1.0 - 1) + 2); // S = 1
        double tsa = 2 * Math.sqrt(A) * alpha;
        double a0 = (A + 1) + (A - 1) * cw + tsa;
        set(A * ((A + 1) - (A - 1) * cw + tsa),
            2 * A * ((A - 1) - (A + 1) * cw),
            A * ((A + 1) - (A - 1) * cw - tsa),
            a0,
            -2 * ((A - 1) + (A + 1) * cw),
            (A + 1) + (A - 1) * cw - tsa);
    }

    public void highShelf(double fs, double f0, double dBgain) {
        double A = Math.pow(10, dBgain / 40.0);
        double w0 = 2 * Math.PI * f0 / fs;
        double cw = Math.cos(w0), sw = Math.sin(w0);
        double alpha = sw / 2.0 * Math.sqrt((A + 1 / A) * (1 / 1.0 - 1) + 2); // S = 1
        double tsa = 2 * Math.sqrt(A) * alpha;
        double a0 = (A + 1) - (A - 1) * cw + tsa;
        set(A * ((A + 1) + (A - 1) * cw + tsa),
            -2 * A * ((A - 1) + (A + 1) * cw),
            A * ((A + 1) + (A - 1) * cw - tsa),
            a0,
            2 * ((A - 1) - (A + 1) * cw),
            (A + 1) - (A - 1) * cw - tsa);
    }

    public void peaking(double fs, double f0, double dBgain, double Q) {
        double A = Math.pow(10, dBgain / 40.0);
        double w0 = 2 * Math.PI * f0 / fs;
        double cw = Math.cos(w0), sw = Math.sin(w0);
        double alpha = sw / (2 * Q);
        double a0 = 1 + alpha / A;
        set(1 + alpha * A,
            -2 * cw,
            1 - alpha * A,
            a0,
            -2 * cw,
            1 - alpha / A);
    }

    public void lowpass(double fs, double f0, double Q) {
        double w0 = 2 * Math.PI * f0 / fs;
        double cw = Math.cos(w0), sw = Math.sin(w0);
        double alpha = sw / (2 * Q);
        double a0 = 1 + alpha;
        set((1 - cw) / 2, 1 - cw, (1 - cw) / 2, a0, -2 * cw, 1 - alpha);
    }

    public void highpass(double fs, double f0, double Q) {
        double w0 = 2 * Math.PI * f0 / fs;
        double cw = Math.cos(w0), sw = Math.sin(w0);
        double alpha = sw / (2 * Q);
        double a0 = 1 + alpha;
        set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, a0, -2 * cw, 1 - alpha);
    }

    private void set(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0;
        this.b1 = b1 / a0;
        this.b2 = b2 / a0;
        this.a1 = a1 / a0;
        this.a2 = a2 / a0;
    }

    /** Filter one sample, advancing the state. */
    public double process(double x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    /** Drop the state (call on discontinuities so old samples don't ring into the new position). */
    public void reset() {
        z1 = 0;
        z2 = 0;
    }
}
