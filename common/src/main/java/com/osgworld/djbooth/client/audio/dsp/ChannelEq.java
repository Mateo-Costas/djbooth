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

    // Where the bands are divided. Pioneer's published 70 / 1000 / 13000 are the frequencies each
    // band's gain range is *quoted at*, not where one band ends and the next begins — so the
    // splits go either side of them: everything under 250 Hz is LOW (measured at 70), 250 Hz to
    // 4 kHz is MID (measured at 1 k), above 4 kHz is HI (measured at 13 k). These match the
    // corners DJ isolators have used since they were built out of active crossovers.
    public static final double F_SPLIT_LOW = 250.0;
    public static final double F_SPLIT_HIGH = 4000.0;
    public static final double EQ_BOOST_DB = 6.0;  // printed on the panel: +6 at the top
    public static final double EQ_CUT_DB = 26.0;   // ... and -26 at the bottom in EQ mode
    public static final double ISO_CUT_DB = 60.0;  // ISOLATOR mode kills the band instead (-inf)

    /** Rebake cadence. See {@link ParamRamp} for why the band positions ramp at all. */
    public static final int CHUNK_FRAMES = ParamRamp.CHUNK_FRAMES;

    // One pair of crossovers per audio channel: LOW|MID, then MID|HI.
    private Crossover[] xLow, xHigh;
    private double sampleRate;
    // Band gains. The targets come from the knobs; the live values chase them one sample at a
    // time. Stepping a gain once per chunk is a discontinuity in amplitude however small the step
    // is, and a discontinuity is a click — the old shelves hid this because a filter's memory
    // smears a coefficient change, while a multiply has no memory at all.
    private double gLow = 1, gMid = 1, gHigh = 1;
    private double tLow = 1, tMid = 1, tHigh = 1;

    /** Per-sample smoothing for the band gains: about a 2 ms glide at 48 kHz. */
    private double gainSmooth = 0.0104;

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
        // A fixed 2 ms glide whatever the sample rate, rather than a fixed number of samples.
        this.gainSmooth = 1.0 - Math.exp(-1.0 / (0.002 * Math.max(1, sampleRate)));
        xLow = new Crossover[channels];
        xHigh = new Crossover[channels];
        for (int c = 0; c < channels; c++) {
            xLow[c] = new Crossover();
            xLow[c].set(sampleRate, F_SPLIT_LOW);
            xHigh[c] = new Crossover();
            xHigh[c].set(sampleRate, F_SPLIT_HIGH);
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
        if (sLow != aLow || sMid != aMid || sHigh != aHigh || iso != aIsolator) {
            // Only the three gains change. The crossover corners are fixed, so unlike the old
            // shelves there are no filter coefficients to rebake when a knob moves — which also
            // means a knob sweep cannot make the filters ring.
            tLow = gainFor(sLow, iso);
            tMid = gainFor(sMid, iso);
            tHigh = gainFor(sHigh, iso);
            aLow = sLow; aMid = sMid; aHigh = sHigh; aIsolator = iso;
        }
    }

    private static double gainFor(double knob, boolean isolator) {
        double db = dbForBand(knob, isolator);
        // The bottom of the travel is a kill, so make it silence rather than a very quiet band.
        if (knob <= 0.0) {
            return isolator ? 0.0 : Math.pow(10.0, -EQ_CUT_DB / 20.0);
        }
        return Math.pow(10.0, db / 20.0);
    }

    /**
     * Run one sample of one channel through the isolator.
     *
     * <p>Split into three, scale each, add back up. This is what the hardware does and what the
     * previous arrangement — a low shelf, a bell and a high shelf in series — could not do. Those
     * three overlapped, so the bands fought each other: killing MID pulled 12 dB out at 500 Hz and
     * 2 kHz as well, and with all three knobs at zero the mixer still passed audio at -6 dB around
     * 250 Hz, where the shelves' skirts left a gap that nothing was cutting. A crossover has no
     * gap by construction: every frequency belongs to exactly one band, so all three down really
     * is silence.
     */
    public double process(int channel, double s) {
        gLow += (tLow - gLow) * gainSmooth;
        gMid += (tMid - gMid) * gainSmooth;
        gHigh += (tHigh - gHigh) * gainSmooth;

        Crossover xl = xLow[channel];
        Crossover xh = xHigh[channel];
        xl.split(s);
        double lowBand = xl.low();
        xh.split(xl.high());
        // The low band skipped the second crossover, so run it through that crossover's allpass
        // to keep all three bands phase-aligned. Without this they no longer sum flat.
        // No bypass at the detent, though it was tried. Splitting a signal and adding it back up
        // is flat in level but not in phase, so switching between the dry signal and the summed
        // bands is a step even though both are the same loudness — it measured as a click 30x
        // worse than the one the smoothing above exists to prevent. Every full-kill isolator
        // shifts phase at its centre detent for exactly this reason; the audio always goes
        // through the filters, as it does on the hardware.
        return gLow * xh.allpass(lowBand) + gMid * xh.low() + gHigh * xh.high();
    }

    /** Drop the filter state (call on discontinuities so old samples don't ring into the new spot). */
    public void reset() {
        for (int c = 0; c < xLow.length; c++) {
            xLow[c].reset();
            xHigh[c].reset();
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
