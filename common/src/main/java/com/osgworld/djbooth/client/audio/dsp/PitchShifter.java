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
 * alone would run off the end of the buffer, so there are two read taps half a window apart, and
 * the output crossfades between them — whichever tap is about to wrap is the one being faded out.
 * The seam lands where the window is quietest, which is why it isn't audible.
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
        readOffset = window;
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
            readOffset = window; // keep the taps parked so switching on doesn't jump
            return s;
        }

        // The read head moves at `ratio` relative to the write head, so the gap between them
        // drifts by (ratio - 1) every sample. Wrapping it by one window is what makes the two
        // taps line up for the crossfade.
        readOffset -= ratio - 1.0;
        if (readOffset >= window * 2.0) {
            readOffset -= window;
        } else if (readOffset < window * 0.0) {
            readOffset += window;
        }

        double a = sampleAt(readOffset);
        double b = sampleAt(readOffset + window);
        // Raised cosine, so the two taps sum to constant power through the seam.
        double phase = (readOffset % window) / (double) window;
        double fade = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        return a * (1.0 - fade) + b * fade;
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
        readOffset = window;
    }
}
