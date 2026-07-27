package com.osgworld.djbooth.client.audio.dsp;

/**
 * The three-band channel EQ of a DJM-900NXS2: a low shelf, a mid bell and a high shelf per audio
 * channel, plus the smoothing and output limiting that keep a knob turn from being audible as
 * anything other than the EQ moving.
 *
 * <p>Lives apart from {@link DspSfxEngine} because that class extends a WaterMedia type, and
 * WaterMedia is {@code compileOnly}: it is absent at test runtime, so anything touching it cannot
 * be tested. Everything here is plain Java and is covered by {@code ChannelEqTest}.
 *
 * <p>Threading matches the engine: {@link #setTargets} is called from the client thread, everything
 * else from the audio thread.
 */
public final class ChannelEq {
    // The centres Pioneer publishes for the DJM-900NXS2's channel EQ: LOW 70 Hz, MID 1 kHz,
    // HI 13 kHz. This code used to run 200 / 632 / 2000, which is a different equaliser wearing
    // the same labels, and it is why each band sounded wrong in its own way:
    //   - LOW at 200 Hz sits on the fundamental of most instruments, not under them, so boosting
    //     it added huge energy (measured: the loudest of the three) and cutting it hollowed the
    //     track out instead of removing bass.
    //   - MID at 632 Hz is the boxy, nasal region — boosting it is the "underwater" sound.
    //   - HI at 2 kHz is presence and sibilance, which is harsh when lifted. Air lives at 13 kHz.
    public static final double F_LOW = 70.0;
    public static final double F_MID = 1000.0;
    public static final double F_HIGH = 13000.0;
    // Wide enough to cover the gap between the two shelves without a narrow, phasey peak.
    public static final double MID_Q = 0.7;
    public static final double EQ_BOOST_DB = 6.0;  // printed on the panel: +6 at the top
    public static final double EQ_CUT_DB = 26.0;   // ... and -26 at the bottom in EQ mode
    public static final double ISO_CUT_DB = 60.0;  // ISOLATOR mode kills the band instead (-inf)

    /** Rebake cadence. See {@link ParamRamp} for why the band positions ramp at all. */
    public static final int CHUNK_FRAMES = ParamRamp.CHUNK_FRAMES;

    private Biquad[] low, mid, high;
    private double sampleRate;

    // Knob targets, written from the client thread.
    private volatile float pLow = 0.5f, pMid = 0.5f, pHigh = 0.5f;
    private volatile boolean pIsolator;

    // Positions actually in the coefficients, audio thread only.
    private final ParamRamp rLow = new ParamRamp();
    private final ParamRamp rMid = new ParamRamp();
    private final ParamRamp rHigh = new ParamRamp();

    // Last positions baked, so a still knob costs nothing.
    private double aLow = -1, aMid = -1, aHigh = -1;
    private boolean aIsolator;

    /** Allocate one filter chain per audio channel and forget any previous ramp. */
    public void setup(int sampleRate, int channels) {
        this.sampleRate = sampleRate;
        low = new Biquad[channels];
        mid = new Biquad[channels];
        high = new Biquad[channels];
        for (int c = 0; c < channels; c++) {
            low[c] = new Biquad();
            mid[c] = new Biquad();
            high[c] = new Biquad();
        }
        aLow = aMid = aHigh = -1; // force a rebake
        rLow.unprime();           // and snap the ramps rather than sweeping up to the live knobs
        rMid.unprime();
        rHigh.unprime();
    }

    /** Where the knobs are now. Each value is 0..1 with 0.5 flat. */
    public void setTargets(float lowV, float midV, float highV, boolean isolator) {
        this.pLow = lowV;
        this.pMid = midV;
        this.pHigh = highV;
        this.pIsolator = isolator;
    }

    /**
     * Step the band positions one notch toward the knobs and rebake if they moved. Call every
     * {@link #CHUNK_FRAMES} frames so a knob sweep becomes a ramp rather than a stair.
     */
    public void advance() {
        boolean iso = pIsolator;
        double sLow = rLow.advance(pLow);
        double sMid = rMid.advance(pMid);
        double sHigh = rHigh.advance(pHigh);
        if (sLow == aLow && sMid == aMid && sHigh == aHigh && iso == aIsolator) {
            return;
        }
        for (int c = 0; c < low.length; c++) {
            low[c].lowShelf(sampleRate, F_LOW, dbForBand(sLow, iso));
            mid[c].peaking(sampleRate, F_MID, dbForBand(sMid, iso), MID_Q);
            high[c].highShelf(sampleRate, F_HIGH, dbForBand(sHigh, iso));
        }
        aLow = sLow; aMid = sMid; aHigh = sHigh; aIsolator = iso;
    }

    /** Run one sample of one channel through all three bands. */
    public double process(int channel, double s) {
        s = low[channel].process(s);
        s = mid[channel].process(s);
        return high[channel].process(s);
    }

    /** Drop the filter state (call on discontinuities so old samples don't ring into the new spot). */
    public void reset() {
        for (int c = 0; c < low.length; c++) {
            low[c].reset();
            mid[c].reset();
            high[c].reset();
        }
    }

    /**
     * Knob 0..1 -&gt; band gain in dB, as printed on the DJM-900NXS2: centre is flat, the top of the
     * travel is +6 dB, and the bottom is -26 dB in EQ mode or a kill in ISOLATOR mode.
     */
    public static double dbForBand(double v, boolean isolator) {
        if (v >= 0.5) {
            return (v - 0.5) / 0.5 * EQ_BOOST_DB;
        }
        return (v / 0.5 - 1.0) * (isolator ? ISO_CUT_DB : EQ_CUT_DB);
    }

    /**
     * TRIM knob 0..1 -&gt; a linear gain, with centre at unity.
     *
     * <p>Laid out in decibels rather than as a straight multiply. A linear law spends its whole
     * lower half between silence and unity, so the bottom of the throw collapses to nothing while
     * the top barely moves — which is exactly how the old {@code knob * 2} behaved. In dB the knob
     * is even-handed: every equal turn is an equal change in loudness, the way a real trim pot is
     * tapered.
     */
    public static double trimGain(double knob) {
        double v = Math.max(0.0, Math.min(1.0, knob));
        double db = v >= 0.5
                ? (v - 0.5) / 0.5 * TRIM_BOOST_DB
                : (v / 0.5 - 1.0) * TRIM_CUT_DB;
        double g = Math.pow(10.0, db / 20.0);
        // Fade the last sliver of travel to true silence. Without it the knob stops at -26 dB,
        // which is quiet but still audible, so "all the way down" would not actually be off.
        // Doing it as a fade rather than a step keeps the bottom of the sweep smooth.
        if (v < FADE_OUT) {
            g *= v / FADE_OUT;
        }
        return g;
    }

    /** Headroom the trim can add, matching the EQ's own boost. */
    public static final double TRIM_BOOST_DB = 6.0;
    /**
     * How far down the bottom half of the trim reaches.
     *
     * <p>Matched to the EQ's own cut so both halves of the panel behave alike. It was 40 dB, which
     * made the knob lopsided: a notch up was worth +0.5 dB and a notch down -3.2 dB, so the bottom
     * of the travel still collapsed — the same complaint the old linear law caused, just milder.
     */
    public static final double TRIM_CUT_DB = 26.0;

    private static final double FADE_OUT = 0.02;
}
