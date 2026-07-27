package com.osgworld.djbooth.client.audio.dsp;

/**
 * The FX knob on each channel strip: a fixed-time feedback echo.
 *
 * <p>A class of its own for the same reason as {@link ChannelEq}: it used to live inline in
 * {@link DspSfxEngine}, which extends a WaterMedia type and so cannot be loaded in a test, and the
 * only way to measure it was to copy the arithmetic into the test and measure the copy. A copy
 * proves nothing about what ships — the same duplication caused two separate faults in the panel
 * geometry — so the echo now lives where both the engine and the tests can reach the real thing.
 */
public final class ChannelEcho {

    /** Repeat time. Fixed, matching a slow beat echo. */
    public static final double ECHO_SECONDS = 0.35;

    private float[] line = new float[1];
    private int pos;

    public void setup(double sampleRate) {
        line = new float[Math.max(1, (int) (sampleRate * ECHO_SECONDS))];
        pos = 0;
        reset();
    }

    public void reset() {
        java.util.Arrays.fill(line, 0f);
        pos = 0;
    }

    /**
     * One sample through the echo.
     *
     * @param knob the FX knob, 0..1; 0 is bypass
     *
     * <p>The knob trades dry for wet rather than adding wet on top. Simply summing the repeats
     * measured 1.28 at the output with the knob up — level above full scale that the limiter then
     * had to take straight back off, so turning the echo up mostly turned the limiter on.
     * Feeding the line with {@code (1 - feedback)} of the input is the same rule the beat echoes
     * follow: a loop that returns g settles at 1/(1-g), so scaling the way in keeps the tail
     * length while leaving the level where the DJ set it.
     */
    public double process(double s, double knob) {
        double k = Math.max(0.0, Math.min(1.0, knob));
        if (k <= 1e-4) {
            return s;
        }
        double mix = k * 0.6;
        double feedback = k * 0.5;
        double echoed = line[pos];
        double out = s * (1.0 - 0.5 * mix) + mix * echoed;
        line[pos] = (float) (s * (1.0 - feedback) + feedback * echoed);
        pos = (pos + 1) % line.length;
        return out;
    }
}
