package com.osgworld.djbooth.client.audio.dsp;

/**
 * A Linkwitz-Riley 4th-order split: one input, a low half and a high half that add back up to the
 * original signal.
 *
 * <p>Two Butterworth sections in cascade per side. That specific pairing is what makes the two
 * halves sum flat — each is 6 dB down at the corner and they are in phase there, so 0.5 + 0.5 = 1
 * rather than the +3 dB bump or the notch other filter pairs give. It is the standard way to build
 * a speaker crossover, and a DJ isolator is a crossover whose bands are summed back together with
 * a level control on each.
 */
public final class Crossover {

    /** Butterworth Q: the value that makes two cascaded sections a Linkwitz-Riley pair. */
    private static final double BUTTERWORTH_Q = 0.70710678118654752;

    private final Biquad lp1 = new Biquad(), lp2 = new Biquad();
    private final Biquad hp1 = new Biquad(), hp2 = new Biquad();
    // The allpass path needs its own filters. Reusing the ones above would interleave two
    // different signals through the same delay elements and corrupt both.
    private final Biquad apLp1 = new Biquad(), apLp2 = new Biquad();
    private final Biquad apHp1 = new Biquad(), apHp2 = new Biquad();

    private double low, high;

    public void set(double sampleRate, double cutoffHz) {
        double f = Math.max(10.0, Math.min(cutoffHz, sampleRate * 0.45));
        for (Biquad b : new Biquad[]{lp1, lp2, apLp1, apLp2}) {
            b.lowpass(sampleRate, f, BUTTERWORTH_Q);
        }
        for (Biquad b : new Biquad[]{hp1, hp2, apHp1, apHp2}) {
            b.highpass(sampleRate, f, BUTTERWORTH_Q);
        }
    }

    /** Split one sample; read the halves with {@link #low()} and {@link #high()}. */
    public void split(double x) {
        low = lp2.process(lp1.process(x));
        high = hp2.process(hp1.process(x));
    }

    public double low() {
        return low;
    }

    public double high() {
        return high;
    }

    /**
     * Pass a signal through this crossover's phase response without splitting it.
     *
     * <p>Needed because the band that skips a crossover still has to be delayed like the ones that
     * went through it. A Linkwitz-Riley low and high summed together is an allpass — flat level,
     * but the phase twist of the split — so running the untouched band through both sides and
     * adding them puts every band back on the same phase footing. Without it the bands do not sum
     * flat any more and the seams between them either dip or bump.
     */
    public double allpass(double x) {
        return apLp2.process(apLp1.process(x)) + apHp2.process(apHp1.process(x));
    }

    public void reset() {
        for (Biquad b : new Biquad[]{lp1, lp2, hp1, hp2, apLp1, apLp2, apHp1, apHp2}) {
            b.reset();
        }
    }
}
