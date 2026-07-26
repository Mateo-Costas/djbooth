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

    // CDJ-3000 controls measured off the panel art: the DIRECTION switch and the transport
    // buttons run down the left edge, SLIP/QUANTIZE sit under the USB slot, and the tempo
    // buttons flank the fader on the right.
    public static final Rect DECK_DIRECTION = new Rect(0.045f, 0.545f, 0.085f, 0.045f);
    public static final Rect DECK_SLIP      = new Rect(0.040f, 0.205f, 0.055f, 0.026f);
    public static final Rect DECK_QUANTIZE  = new Rect(0.103f, 0.205f, 0.062f, 0.026f);
    public static final Rect DECK_JOGMODE   = new Rect(0.880f, 0.430f, 0.070f, 0.030f);
    public static final Rect DECK_TEMPO_RESET = new Rect(0.795f, 0.640f, 0.045f, 0.030f);
    public static final Rect DECK_BEAT_SYNC = new Rect(0.855f, 0.348f, 0.062f, 0.028f);
    public static final Rect DECK_TRACK_START = new Rect(0.045f, 0.612f, 0.085f, 0.030f);
    public static final Rect DECK_SEARCH_BACK = new Rect(0.045f, 0.652f, 0.040f, 0.030f);
    public static final Rect DECK_SEARCH_FWD  = new Rect(0.090f, 0.652f, 0.040f, 0.030f);
    public static final Rect DECK_CALL_PREV = new Rect(0.560f, 0.348f, 0.038f, 0.026f);
    public static final Rect DECK_CALL_NEXT = new Rect(0.602f, 0.348f, 0.038f, 0.026f);
    public static final Rect DECK_MEM_DELETE = new Rect(0.660f, 0.348f, 0.045f, 0.026f);
    public static final Rect DECK_MEMORY    = new Rect(0.712f, 0.348f, 0.052f, 0.026f);

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
    // SOUND COLOR FX bank on the left of the DJM: six mode buttons in two columns of three,
    // with the PARAMETER knob underneath them (measured x 0.055..0.135, y 0.455..0.560).
    public static final Rect MIX_COLOR_MODES = new Rect(0.040f, 0.452f, 0.110f, 0.105f);
    public static final Rect MIX_COLOR_PARAM = new Rect(0.073f, 0.576f, 0.044f, 0.035f);

    // BEAT FX panel down the right-hand edge of the DJM, measured off the panel art:
    // beat-fraction buttons y≈0.363..0.410, TAP y≈0.494, FX FREQUENCY y≈0.555, the effect
    // selector y≈0.625, channel selector y≈0.681, TIME y≈0.756, LEVEL/DEPTH y≈0.836,
    // and the big ON/OFF at y≈0.915.
    public static final Rect FX_BEATS     = new Rect(0.812f, 0.360f, 0.160f, 0.052f);
    public static final Rect FX_TAP       = new Rect(0.862f, 0.478f, 0.060f, 0.032f);
    public static final Rect FX_FREQ      = new Rect(0.812f, 0.542f, 0.160f, 0.026f);
    public static final Rect FX_TYPES     = new Rect(0.800f, 0.590f, 0.185f, 0.075f);
    public static final Rect FX_CHANNEL   = new Rect(0.862f, 0.672f, 0.060f, 0.028f);
    public static final Rect FX_DEPTH     = new Rect(0.870f, 0.820f, 0.044f, 0.035f);
    public static final Rect FX_ONOFF     = new Rect(0.858f, 0.898f, 0.068f, 0.038f);

    // Global switches (bottom-right of the DJM: EQ CURVE + CH FADER curve).
    public static final Rect MIX_ISOLATOR    = new Rect(0.718f, 0.712f, 0.066f, 0.030f);
    public static final Rect MIX_FADERCURVE  = new Rect(0.718f, 0.766f, 0.066f, 0.030f);
    public static final Rect MIX_XFCURVE     = new Rect(0.718f, 0.820f, 0.066f, 0.030f);

    // Channel level meters: the LED strips beside each strip, measured x just left of the knobs,
    // running y 0.205 (+12 dB) .. 0.455 (-27 dB).
    public static final Rect MIX_METER_A = new Rect(0.196f, 0.205f, 0.011f, 0.250f);
    public static final Rect MIX_METER_B = new Rect(0.326f, 0.205f, 0.011f, 0.250f);

    // Master column: BALANCE knob, its CUE, and the BOOTH MONITOR knob below them.
    public static final Rect MIX_BALANCE = new Rect(0.729f, 0.474f, 0.044f, 0.035f);
    public static final Rect MIX_BOOTH   = new Rect(0.729f, 0.636f, 0.044f, 0.035f);
    public static final Rect MIX_CUE_A   = new Rect(0.215f, 0.554f, 0.064f, 0.022f);
    public static final Rect MIX_CUE_B   = new Rect(0.345f, 0.554f, 0.064f, 0.022f);
}
