package com.osgworld.djbooth.mixer;

/**
 * The mixer's level arithmetic: how a channel fader, the crossfader and the master combine into
 * the volume one deck actually plays at.
 *
 * <p>Pure functions with no Minecraft behind them, so the sums that decide whether anyone hears
 * anything can be tested directly rather than only by ear.
 *
 * <p>Where each gain sits in the signal path is taken from the DJM-900NXS2 operating instructions
 * (see {@code docs/design/djm-900nxs2-reference.md}): the channel level meter reads "before the
 * channel faders", and a BEAT FX that sits on a channel comes after that channel's fader, which is
 * why lowering the fader leaves the effect's tail ringing.
 */
public final class MixLevels {
    private MixLevels() {}

    /** Channel fader curves, as the three icons printed beside the CH FADER switch. */
    public static final int CURVE_SLOW = 0;
    public static final int CURVE_LINEAR = 1;
    public static final int CURVE_SHARP = 2;
    public static final String[] CURVE_NAMES = {"SLOW", "LIN", "SHARP"};

    /**
     * Crossfader curves, as the three icons printed beside the CROSS FADER switch. These are not
     * the channel fader's shapes: a crossfader curve is about how the two sides trade places, and
     * the one built for scratching leaves <em>both</em> sides at full for most of the throw.
     */
    public static final int XF_CURVE_SLOW = 0;
    public static final int XF_CURVE_MEDIUM = 1;
    public static final int XF_CURVE_FAST = 2;
    public static final String[] XF_CURVE_NAMES = {"SLOW", "MED", "FAST"};

    // Pioneer publishes no numbers for these, only the descriptions in the manual. The shapes come
    // from those descriptions and from Rane's note on crossfader tapers (RaneNote 146): the smooth
    // curve is the classic constant-power one, and the sharp one goes from off to full over "less
    // than .1 inch" of a fader, which on a 45 mm crossfader is about 5.5% of the travel. Both
    // constants below are estimates in that spirit, not measurements of a DJM.
    private static final double XF_MEDIUM_FULL_AT = 0.55; // fraction of travel where MED reaches full
    private static final double XF_FAST_WIDTH = 0.06;     // fraction of travel FAST takes to open

    /** CROSS FADER ASSIGN positions, as printed on the switch under each channel fader. */
    public static final int XF_A = 0;
    public static final int XF_THRU = 1;
    public static final int XF_B = 2;

    /**
     * Clamp to 0..1, with NaN becoming 0.
     *
     * <p>The NaN case is not academic: values arrive from clients, and {@code Math.min}/{@code max}
     * both pass NaN straight through. A single NaN fader would be saved into the mixer and silence
     * or corrupt the deck for every player until someone edited the world file by hand.
     */
    public static float clamp01(float v) {
        return v > 0f ? (v < 1f ? v : 1f) : 0f;
    }

    /**
     * Shape a 0..1 channel fader position by one of the three printed curves.
     *
     * <p>All three agree at the ends - fully down is silence and fully up is unity - and differ in
     * between: SHARP stays quiet until the top of the throw, which is what makes it a cutting
     * curve, while SLOW opens up early for long blends.
     */
    public static float curve(float v, int shape) {
        float x = clamp01(v);
        return switch (shape) {
            case CURVE_SHARP -> x * x * x;
            case CURVE_SLOW -> (float) Math.sqrt(x);
            default -> x;
        };
    }

    /**
     * Gain for one side of the crossfader.
     *
     * @param p how far the fader is toward this side: 1 with it pushed all the way to this side,
     *          0 with it at the far end
     * @param curve one of the {@code XF_CURVE_*} shapes
     */
    public static float crossfaderSide(float p, int curve) {
        double x = clamp01(p);
        return (float) switch (curve) {
            // Cut: open almost at once, then both sides stay at full across the middle.
            case XF_CURVE_FAST -> Math.min(1.0, x / XF_FAST_WIDTH);
            // Between the two: full a little past halfway, so the overlap is short.
            case XF_CURVE_MEDIUM -> Math.sin(Math.min(1.0, x / XF_MEDIUM_FULL_AT) * Math.PI / 2);
            // Blend: constant power. Each side is -3 dB at the centre and the powers sum to one.
            default -> Math.sin(x * Math.PI / 2);
        };
    }

    /**
     * How much the crossfader lets a channel through, given the side it is assigned to.
     *
     * <p>THRU takes the channel off the crossfader entirely, exactly like the hardware switch: the
     * fader can be anywhere and the channel still plays.
     */
    public static float crossfaderWeight(int assign, float crossfader, int crossfaderCurve) {
        return switch (assign) {
            case XF_A -> crossfaderSide(1.0f - clamp01(crossfader), crossfaderCurve);
            case XF_B -> crossfaderSide(clamp01(crossfader), crossfaderCurve);
            default -> 1.0f; // THRU
        };
    }

    /**
     * Volume for one channel out on the floor: its fader, its crossfader assignment, and master.
     * The product of everything between the channel and the speakers, wherever each part is applied.
     */
    public static float channelVolume(float channelFader, int channelCurve,
                                      int assign, float crossfader, int crossfaderCurve,
                                      float master) {
        return clamp01(curve(channelFader, channelCurve)
                * crossfaderWeight(assign, crossfader, crossfaderCurve)
                * clamp01(master));
    }

    /**
     * A channel's level split into the part applied <em>before</em> its BEAT FX and the part
     * applied after it.
     *
     * @param pre  gain ahead of the effect: the audio that goes into it
     * @param post gain after the effect: what reaches the speakers
     */
    public record Gains(float pre, float post) {
        /** What the two multiply out to, which is the volume the channel would have had anyway. */
        public float total() {
            return pre * post;
        }
    }

    /**
     * Work out a channel's {@link Gains} for one listener.
     *
     * <p>The channel fader always comes before the effect. The crossfader comes before it only when
     * the effect is on MASTER, which sits after the whole mix; an effect on the deck's own channel
     * comes ahead of the crossfader (the manual has separate CROSS FADER A and B effect targets for
     * the other case). Master level is last of all.
     *
     * <p>Out at the booth the rules change. BOOTH MONITOR has its own level and is not scaled by
     * MASTER LEVEL: the manual lists BOOTH among the outputs BALANCE acts on, but MASTER LEVEL acts
     * only on the MASTER outputs. And a cued channel is heard <em>before</em> its fader - that is
     * what CUE is for, lining a track up while its fader is still down - and apart from the
     * crossfader; any channel that is not cued drops out while something is.
     *
     * @param effectOnMaster whether BEAT FX is patched across MASTER rather than this channel
     * @param atBooth        whether the listener is standing at the booth rather than on the floor
     * @param anyCued        whether any channel's CUE is lit
     * @param thisCued       whether this channel's CUE is lit
     */
    public static Gains gains(boolean effectOnMaster,
                              float channelFader, int channelCurve,
                              int assign, float crossfader, int crossfaderCurve,
                              float master,
                              boolean atBooth, float boothLevel,
                              boolean anyCued, boolean thisCued) {
        float fader = curve(channelFader, channelCurve);
        float xf = crossfaderWeight(assign, crossfader, crossfaderCurve);
        float pre = effectOnMaster ? fader * xf : fader;
        float afterEffect = effectOnMaster ? 1.0f : xf;

        if (!atBooth) {
            return new Gains(clamp01(pre), clamp01(afterEffect * clamp01(master)));
        }
        if (anyCued) {
            return new Gains(thisCued ? 1.0f : 0.0f, clamp01(boothLevel));
        }
        return new Gains(clamp01(pre), clamp01(afterEffect * clamp01(boothLevel)));
    }
}
