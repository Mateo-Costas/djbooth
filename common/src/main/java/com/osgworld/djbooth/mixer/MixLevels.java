package com.osgworld.djbooth.mixer;

/**
 * The mixer's level arithmetic: how a channel fader, the crossfader and the master combine into
 * the volume one deck actually plays at.
 *
 * <p>Pure functions with no Minecraft behind them, so the sums that decide whether anyone hears
 * anything can be tested directly rather than only by ear.
 */
public final class MixLevels {
    private MixLevels() {}

    /** Fader curves, as the three icons printed beside each switch. */
    public static final int CURVE_SLOW = 0;
    public static final int CURVE_LINEAR = 1;
    public static final int CURVE_SHARP = 2;
    public static final String[] CURVE_NAMES = {"SLOW", "LIN", "SHARP"};

    /** CROSS FADER ASSIGN positions, as printed on the switch under each channel fader. */
    public static final int XF_A = 0;
    public static final int XF_THRU = 1;
    public static final int XF_B = 2;

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Shape a 0..1 fader position by one of the three printed curves.
     *
     * <p>All three agree at the ends — fully down is silence and fully up is unity — and differ in
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
     * How much the crossfader lets a channel through, given the side it is assigned to.
     *
     * <p>THRU takes the channel off the crossfader entirely, exactly like the hardware switch: the
     * fader can be anywhere and the channel still plays.
     */
    public static float crossfaderWeight(int assign, float crossfader, int crossfaderCurve) {
        return switch (assign) {
            case XF_A -> curve(1.0f - clamp01(crossfader), crossfaderCurve);
            case XF_B -> curve(clamp01(crossfader), crossfaderCurve);
            default -> 1.0f; // THRU
        };
    }

    /**
     * Volume for one channel out on the floor: its fader, its crossfader assignment, and master.
     */
    public static float channelVolume(float channelFader, int channelCurve,
                                      int assign, float crossfader, int crossfaderCurve,
                                      float master) {
        return clamp01(curve(channelFader, channelCurve)
                * crossfaderWeight(assign, crossfader, crossfaderCurve)
                * clamp01(master));
    }

    /**
     * Volume for one channel as heard at the booth.
     *
     * <p>A real desk feeds the booth monitors from their own knob and puts whatever is cued in the
     * DJ's headphones. Here the "booth" is the blocks around the mixer, so cueing a channel
     * previews it for whoever is stood there and nobody else. As soon as anything is cued, the
     * booth hears the cued channels and only those — which is what makes CUE useful for lining a
     * track up before bringing it in.
     */
    public static float boothVolume(float floorVolume, float boothLevel,
                                    boolean anythingCued, boolean thisChannelCued) {
        if (anythingCued) {
            return thisChannelCued ? clamp01(boothLevel) : 0f;
        }
        return clamp01(floorVolume * clamp01(boothLevel));
    }
}
