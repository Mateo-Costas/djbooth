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
    public static final Rect DECK_PLAY    = new Rect(0.150f, 0.850f, 0.110f, 0.110f); // green play/pause
    public static final Rect DECK_CUE     = new Rect(0.150f, 0.705f, 0.110f, 0.110f); // orange cue
    public static final Rect DECK_LOOP    = new Rect(0.270f, 0.385f, 0.095f, 0.060f); // reloop/exit
    public static final Rect DECK_TEMPO   = new Rect(0.775f, 0.585f, 0.050f, 0.260f); // vertical tempo fader
    public static final Rect DECK_SCREEN  = new Rect(0.120f, 0.150f, 0.620f, 0.190f); // for live readout

    // --- Mixer controls (relative to the mixer region, placeholder panel) ---
    public static final Rect MIX_FADER_A  = new Rect(0.18f, 0.35f, 0.10f, 0.42f); // vertical
    public static final Rect MIX_FADER_B  = new Rect(0.45f, 0.35f, 0.10f, 0.42f); // vertical
    public static final Rect MIX_MASTER   = new Rect(0.75f, 0.15f, 0.10f, 0.42f); // vertical
    public static final Rect MIX_XFADER   = new Rect(0.15f, 0.82f, 0.60f, 0.08f); // horizontal crossfader
}
