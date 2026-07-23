package com.osgworld.djbooth.client.screen;

/**
 * Normalized layout of the booth panel. All coordinates are fractions (0..1):
 * regions are relative to the whole panel; controls are relative to their region.
 * When the real artwork arrives, only these numbers need tweaking — no logic changes.
 */
public final class BoothLayout {
    private BoothLayout() {}

    /** A normalized rectangle. */
    public record Rect(float x, float y, float w, float h) {}

    // --- Regions (relative to the full panel; booth.png is 1200x440) ---
    // deck A = x[10..430], mixer = x[445..755], deck B = x[770..1190].
    public static final Rect REGION_DECK_A = new Rect(0.0083f, 0.0227f, 0.3500f, 0.9545f);
    public static final Rect REGION_MIXER  = new Rect(0.3708f, 0.0227f, 0.2583f, 0.9545f);
    public static final Rect REGION_DECK_B = new Rect(0.6417f, 0.0227f, 0.3500f, 0.9545f);

    // --- Deck controls (relative to a deck region, aligned to the CDJ-3000 art) ---
    public static final Rect DECK_JOG     = new Rect(0.250f, 0.400f, 0.520f, 0.520f);
    public static final Rect DECK_PLAY    = new Rect(0.168f, 0.865f, 0.075f, 0.075f); // green play/pause
    public static final Rect DECK_CUE     = new Rect(0.168f, 0.735f, 0.075f, 0.075f); // orange cue
    public static final Rect DECK_LOOP    = new Rect(0.270f, 0.385f, 0.095f, 0.060f); // reloop/exit
    public static final Rect DECK_TEMPO   = new Rect(0.775f, 0.585f, 0.050f, 0.260f); // vertical tempo fader
    public static final Rect DECK_SCREEN  = new Rect(0.166f, 0.180f, 0.619f, 0.120f); // live readout over the CDJ screen
    public static final Rect DECK_URLBAR  = new Rect(0.170f, 0.055f, 0.610f, 0.058f); // track URL input, over the CDJ display title bar

    // --- Mixer controls (relative to the mixer region, aligned to the DJM-900NXS2 art) ---
    // Measured device fractions: ch1 x=0.251, ch2 x=0.380; channel fader travel y=0.673..0.835;
    // crossfader centered x=0.44 y=0.925; master level knob x=0.75 y=0.12.
    public static final Rect MIX_FADER_A  = new Rect(0.226f, 0.673f, 0.050f, 0.162f); // channel 1 (deck A)
    public static final Rect MIX_FADER_B  = new Rect(0.355f, 0.673f, 0.050f, 0.162f); // channel 2 (deck B)
    public static final Rect MIX_MASTER   = new Rect(0.725f, 0.055f, 0.050f, 0.130f); // master level (over knob)
    public static final Rect MIX_XFADER   = new Rect(0.340f, 0.905f, 0.200f, 0.045f); // MAGVEL crossfader

    // EQ + COLOUR filter knobs, stacked above each channel fader (DJM strip: filter, HI, MID, LOW).
    // Channel columns align with the faders (A x≈0.226, B x≈0.355); tweak Y to match the art.
    private static final float KNOB_W = 0.050f, KNOB_H = 0.045f;
    private static Rect knob(float x, float y) { return new Rect(x, y, KNOB_W, KNOB_H); }

    public static final Rect MIX_FILTER_A = knob(0.226f, 0.270f); // COLOR
    public static final Rect MIX_HI_A     = knob(0.226f, 0.395f);
    public static final Rect MIX_MID_A    = knob(0.226f, 0.495f);
    public static final Rect MIX_LOW_A    = knob(0.226f, 0.595f);
    public static final Rect MIX_FILTER_B = knob(0.355f, 0.270f);
    public static final Rect MIX_HI_B     = knob(0.355f, 0.395f);
    public static final Rect MIX_MID_B    = knob(0.355f, 0.495f);
    public static final Rect MIX_LOW_B    = knob(0.355f, 0.595f);
}
