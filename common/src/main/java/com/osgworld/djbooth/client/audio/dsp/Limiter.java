package com.osgworld.djbooth.client.audio.dsp;

/**
 * The channel's output limiter: a peak detector driving a smooth gain reduction, shared across the
 * audio channels of a frame.
 *
 * <p>What it replaces, and why: the strip used to end in a memoryless soft clipper that started
 * bending at 0.7. A mastered track peaks near 1.0 all the time, so that stage was reshaping the
 * waveform continuously — measured at 5% THD with every EQ knob at centre — and it never let
 * anything out above 0.85. That is audible as a permanent electrical buzz, and it also meant
 * turning a knob up made almost no difference while turning it down worked normally.
 *
 * <p>A limiter avoids that because it does not reshape a waveform at all: it works out how loud
 * the signal is and turns the whole thing down by that much. Gain applied smoothly over
 * milliseconds adds no harmonics, so nothing is coloured until it genuinely runs out of headroom,
 * and then it ducks instead of buzzing.
 *
 * <p>Attack is fast enough to catch a kick drum's first cycle; release is slow enough not to
 * modulate the bass, which is what makes a limiter breathe. Both channels of a frame are driven
 * from whichever is louder, so limiting never pulls the stereo image to one side.
 */
public final class Limiter {

    /** Where limiting starts. Just under full scale: everything below is untouched. */
    public static final double THRESHOLD = 0.97;

    private static final double ATTACK_MS = 1.5;
    private static final double RELEASE_MS = 120.0;

    private double attackCoef, releaseCoef;
    private double gain = 1.0;      // current gain reduction, 1.0 = not limiting
    private double envelope;        // smoothed peak estimate

    public Limiter() {
        setup(48000);
    }

    public void setup(double sampleRate) {
        double fs = Math.max(1.0, sampleRate);
        attackCoef = Math.exp(-1.0 / (ATTACK_MS * 0.001 * fs));
        releaseCoef = Math.exp(-1.0 / (RELEASE_MS * 0.001 * fs));
        reset();
    }

    public void reset() {
        gain = 1.0;
        envelope = 0.0;
    }

    /**
     * Work out the gain this frame should be multiplied by, from the loudest channel in it.
     *
     * <p>Call once per frame with the peak across its channels, then multiply every channel of
     * that frame by the result.
     */
    public double gainFor(double framePeak) {
        double p = Math.abs(framePeak);
        // Follow peaks quickly, let go slowly.
        double coef = p > envelope ? attackCoef : releaseCoef;
        envelope = p + coef * (envelope - p);

        double wanted = envelope > THRESHOLD ? THRESHOLD / envelope : 1.0;
        // Smooth the gain itself too, so a single stray sample cannot step the level.
        double gcoef = wanted < gain ? attackCoef : releaseCoef;
        gain = wanted + gcoef * (gain - wanted);
        return gain;
    }

    /** How far the limiter is pulling the level down right now, in dB (0 = not working). */
    public double reductionDb() {
        return -20 * Math.log10(Math.max(1e-6, gain));
    }

    /**
     * Last-ditch ceiling for a sample that still exceeds full scale.
     *
     * <p>The limiter's envelope lags by design, so a sharp transient can slip through before the
     * gain has come down. Rather than let it wrap round in the converter, curve the last fraction.
     * With the limiter doing the real work this runs vanishingly rarely, which is the point: it is
     * a guard rail, not a sound.
     */
    public static double ceiling(double s) {
        double a = Math.abs(s);
        if (a <= 0.95) {
            return s;
        }
        double over = a - 0.95;
        double curved = 0.95 + 0.05 * (over / (0.05 + over));
        return s < 0 ? -curved : curved;
    }
}
