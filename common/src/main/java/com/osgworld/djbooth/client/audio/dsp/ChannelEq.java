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
    // Band split, matching how a DJM-900NXS2 behaves: LOW below 200 Hz, MID in between, HI above
    // 2 kHz. Pioneer doesn't publish the exact corners, so these are the figures the manual and
    // measurements point at.
    public static final double F_LOW = 200.0;
    public static final double F_HIGH = 2000.0;
    public static final double F_MID = Math.sqrt(F_LOW * F_HIGH); // bell sits between the shelves
    public static final double MID_Q = 0.9;
    public static final double EQ_BOOST_DB = 6.0;  // printed on the panel: +6 at the top
    public static final double EQ_CUT_DB = 26.0;   // ... and -26 at the bottom in EQ mode
    public static final double ISO_CUT_DB = 60.0;  // ISOLATOR mode kills the band instead (-inf)

    /** Rebake cadence. See {@link ParamRamp} for why the band positions ramp at all. */
    public static final int CHUNK_FRAMES = ParamRamp.CHUNK_FRAMES;

    // Boosting a band is +6 dB and the trim is another +6, so a loud track with the LOW up runs
    // past full scale easily. Chopping the waveform flat there squares off every peak, and square
    // edges are broadband harmonics: it buzzes. Bending the top of the range instead keeps the
    // output inside 1.0 without a corner, so an over sounds like a pushed mixer rather than a fault.
    private static final double SOFT_KNEE = 0.7; // linear below this, curved above

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

    /** Keep a sample inside full scale by bending the top of the range rather than cutting it flat. */
    public static double softClip(double s) {
        double a = Math.abs(s);
        if (a <= SOFT_KNEE) {
            return s;
        }
        double range = 1.0 - SOFT_KNEE;
        double over = a - SOFT_KNEE;
        double curved = SOFT_KNEE + range * (over / (range + over)); // -> 1.0, never past it
        return s < 0 ? -curved : curved;
    }
}
