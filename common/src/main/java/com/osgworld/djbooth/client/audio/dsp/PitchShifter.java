package com.osgworld.djbooth.client.audio.dsp;

/**
 * Shifts pitch without changing tempo, which is what a CDJ's MASTER TEMPO button is for.
 *
 * <p>Riding the tempo fader works by playing the track faster or slower, so the pitch goes with it
 * and a track pulled 6% up also sings 6% sharp. MASTER TEMPO cancels that: the deck still plays at
 * the new speed, but this stage shifts the pitch back down by the same ratio, so the tempo moves
 * and the key doesn't.
 *
 * <p>It works by reading a delay line at a different speed from the one it's written at. That
 * alone would run off the end of the buffer, so the read position wraps — and a wrap is a jump in
 * the signal. Hence two read taps, half a buffer apart, which therefore wrap half a cycle apart:
 * at any moment one of them is mid-buffer and perfectly continuous. The output crossfades between
 * them on a raised cosine that falls to zero at each tap's own wrap point, so every jump happens
 * while that tap is silent and none of them are audible.
 *
 * <p>This is the cheap approach and it is honest about its limits: it's transparent over the range
 * a tempo fader actually covers (roughly ±16%), and gets progressively more warbly beyond that.
 * Since MASTER TEMPO only ever has to undo the tempo fader, that is exactly the range that matters.
 */
public final class PitchShifter {
    /** Crossfade window. Long enough not to flutter, short enough not to smear transients. */
    private static final double WINDOW_SECONDS = 0.050;
    /** Ratios closer to unity than this are treated as "off"; the shifter is bypassed. */
    private static final double UNITY_EPSILON = 1e-4;

    private float[] buffer = new float[1];
    private int writePos;
    private double readOffset;   // how far the read taps trail the write head, in samples
    private int window = 1;      // crossfade window length in samples
    private double ratio = 1.0;

    public void setup(double sampleRate) {
        window = Math.max(2, (int) (sampleRate * WINDOW_SECONDS));
        buffer = new float[window * 2];
        writePos = 0;
        readOffset = 0;
        java.util.Arrays.fill(buffer, 0f);
    }

    /**
     * Set the shift ratio: 2.0 is an octave up, 0.5 an octave down, 1.0 bypassed.
     *
     * <p>To cancel a deck playing at {@code rate}, pass {@code 1 / rate}.
     */
    public void setRatio(double newRatio) {
        this.ratio = newRatio > 0 ? newRatio : 1.0;
    }

    public boolean isBypassed() {
        return Math.abs(ratio - 1.0) < UNITY_EPSILON;
    }

    /** Shift one sample. Returns it unchanged while bypassed. */
    public double process(double s) {
        buffer[writePos] = (float) s;
        writePos = (writePos + 1) % buffer.length;
        if (isBypassed()) {
            readOffset = 0; // park the taps so switching on doesn't jump
            return s;
        }

        // The read head moves at `ratio` relative to the write head, so the gap between them
        // drifts by (ratio - 1) every sample. It wraps over the whole buffer, and the second tap
        // sits half a buffer away, so the two taps reach their wrap points half a cycle apart —
        // that stagger is the whole point, because it means one tap is always mid-buffer and
        // continuous while the other jumps.
        double span = buffer.length;
        readOffset -= ratio - 1.0;
        if (readOffset >= span) {
            readOffset -= span;
        } else if (readOffset < 0) {
            readOffset += span;
        }

        double a = sampleAt(readOffset);
        double b = sampleAt(readOffset + window);
        // Raised cosine over the full wrap cycle. It is zero at phase 0 and 1 — exactly where tap
        // a jumps — and one at phase 0.5, where tap b jumps instead. Each tap is therefore silent
        // at its own discontinuity, which is what keeps the handover inaudible.
        double phase = readOffset / span;
        double weightA = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        return a * weightA + b * (1.0 - weightA);
    }

    /** Read the buffer {@code back} samples behind the write head, interpolating between samples. */
    private double sampleAt(double back) {
        double p = writePos - back;
        while (p < 0) {
            p += buffer.length;
        }
        while (p >= buffer.length) {
            p -= buffer.length;
        }
        int i0 = (int) p;
        int i1 = (i0 + 1) % buffer.length;
        double frac = p - i0;
        return buffer[i0] * (1 - frac) + buffer[i1] * frac;
    }

    public void reset() {
        java.util.Arrays.fill(buffer, 0f);
        readOffset = 0;
    }
}
