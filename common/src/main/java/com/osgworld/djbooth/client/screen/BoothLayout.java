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
    public static final Rect DECK_PLAY    = new Rect(0.168f, 0.895f, 0.075f, 0.075f); // green play/pause
    public static final Rect DECK_CUE     = new Rect(0.168f, 0.765f, 0.075f, 0.075f); // orange cue
    public static final Rect DECK_LOOP    = new Rect(0.270f, 0.385f, 0.095f, 0.060f); // reloop/exit
    public static final Rect DECK_TEMPO   = new Rect(0.775f, 0.700f, 0.050f, 0.240f); // vertical tempo fader
    public static final Rect DECK_SCREEN  = new Rect(0.166f, 0.148f, 0.619f, 0.120f); // live readout over the CDJ screen
    public static final Rect DECK_URLBAR  = new Rect(0.170f, 0.055f, 0.610f, 0.058f); // track URL input, over the CDJ display title bar

    // --- Mixer controls (relative to the mixer region, aligned to the DJM-900NXS2 art) ---
    // Measured device fractions: ch1 x=0.251, ch2 x=0.380; channel fader travel y=0.673..0.835;
    // crossfader centered x=0.44 y=0.925; master level knob x=0.75 y=0.12.
    // Channel fader travel measured off the printed 10..0 scale: y 0.673 (10) .. 0.784 (0).
    public static final Rect MIX_FADER_A  = new Rect(0.222f, 0.673f, 0.050f, 0.111f); // channel 1 (deck A)
    public static final Rect MIX_FADER_B  = new Rect(0.352f, 0.673f, 0.050f, 0.111f); // channel 2 (deck B)
    public static final Rect MIX_MASTER   = new Rect(0.715f, 0.185f, 0.065f, 0.230f); // master level (lower + bigger)
    public static final Rect MIX_XFADER   = new Rect(0.340f, 0.905f, 0.200f, 0.045f); // MAGVEL crossfader

    // EQ + COLOR knobs, measured off the DJM-900NXS2 art with a percentage grid (the mixer image
    // fills the region exactly, so image fractions equal these control fractions). Real strip order
    // top->bottom: TRIM, HI, MID, LOW, then the larger COLOR filter. Measured knob centres:
    // channel 1 x=0.246, channel 2 x=0.376; TRIM y=0.178, HI y=0.257, MID y=0.331, LOW y=0.404,
    // COLOR y=0.501. EQ knobs are 0.050 wide, COLOR is 0.065.
    public static final Rect MIX_HI_A     = new Rect(0.221f, 0.236f, 0.050f, 0.041f);
    public static final Rect MIX_MID_A    = new Rect(0.221f, 0.310f, 0.050f, 0.041f);
    public static final Rect MIX_LOW_A    = new Rect(0.221f, 0.383f, 0.050f, 0.041f);
    public static final Rect MIX_FILTER_A = new Rect(0.214f, 0.474f, 0.065f, 0.053f); // COLOR
    public static final Rect MIX_HI_B     = new Rect(0.351f, 0.236f, 0.050f, 0.041f);
    public static final Rect MIX_MID_B    = new Rect(0.351f, 0.310f, 0.050f, 0.041f);
    public static final Rect MIX_LOW_B    = new Rect(0.351f, 0.383f, 0.050f, 0.041f);
    public static final Rect MIX_FILTER_B = new Rect(0.344f, 0.474f, 0.065f, 0.053f); // COLOR

    // TRIM/GAIN knob per channel: top of each strip, where the real DJM puts it (measured y=0.178).
    public static final Rect MIX_GAIN_A   = new Rect(0.224f, 0.161f, 0.044f, 0.035f);
    public static final Rect MIX_GAIN_B   = new Rect(0.354f, 0.161f, 0.044f, 0.035f);

    // CROSS FADER ASSIGN switch under each channel fader (A / THRU / B), measured y=0.845.
    public static final Rect MIX_XF_ASSIGN_A = new Rect(0.224f, 0.833f, 0.045f, 0.025f);
    public static final Rect MIX_XF_ASSIGN_B = new Rect(0.354f, 0.833f, 0.045f, 0.025f);

    // Echo (Beat FX) knob per channel. Not a DJM strip control, so it lives on the right-hand
    // FX panel above the global switches rather than stealing the TRIM position.
    public static final Rect MIX_ECHO_A   = new Rect(0.700f, 0.500f, 0.060f, 0.048f);
    public static final Rect MIX_ECHO_B   = new Rect(0.700f, 0.585f, 0.060f, 0.048f);
    // Global switches (bottom-right of the DJM: EQ CURVE + CH FADER curve).
    public static final Rect MIX_ISOLATOR    = new Rect(0.718f, 0.700f, 0.070f, 0.040f);
    public static final Rect MIX_FADERCURVE  = new Rect(0.718f, 0.770f, 0.070f, 0.040f);
}
