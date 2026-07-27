package com.osgworld.djbooth.mixer;

/**
 * The BEAT FX the DJM-900NXS2 offers, in the order they sit around the effect selector knob, plus
 * the beat fractions its BEAT buttons step through.
 *
 * <p>Kept away from the DSP so the mixer block entity, the network layer and the GUI can name an
 * effect without dragging in client-only audio code.
 */
public final class BeatFxTypes {
    private BeatFxTypes() {}

    public static final int DELAY = 0;
    public static final int ECHO = 1;
    public static final int PING_PONG = 2;
    public static final int SPIRAL = 3;
    public static final int REVERB = 4;
    public static final int TRANS = 5;
    public static final int FILTER = 6;
    public static final int FLANGER = 7;
    public static final int PHASER = 8;
    public static final int PITCH = 9;
    public static final int SLIP_ROLL = 10;
    public static final int ROLL = 11;
    public static final int VINYL_BRAKE = 12;
    public static final int HELIX = 13;
    public static final int TYPES = 14;

    public static final String[] NAMES = {
            "DELAY", "ECHO", "PING PONG", "SPIRAL", "REVERB", "TRANS", "FILTER",
            "FLANGER", "PHASER", "PITCH", "SLIP ROLL", "ROLL", "VINYL BRAKE", "HELIX"
    };

    /** The beat fractions printed on the BEAT buttons, as multiples of one beat. */
    public static final double[] BEATS = {1 / 16.0, 1 / 8.0, 1 / 4.0, 1 / 2.0, 3 / 4.0, 1, 2, 4};
    public static final String[] BEAT_NAMES = {"1/16", "1/8", "1/4", "1/2", "3/4", "1", "2", "4"};
    public static final int DEFAULT_BEAT = 5; // "1"

    /** FX FREQUENCY band buttons: each can be muted out of the effect send. */
    public static final int BAND_LOW = 1;
    public static final int BAND_MID = 2;
    public static final int BAND_HI = 4;
    public static final int BANDS_ALL = BAND_LOW | BAND_MID | BAND_HI;

    /** Which channel the effect is patched across, like the selector knob's positions. */
    public static final int CH_A = 0;
    public static final int CH_B = 1;
    public static final int CH_MASTER = 2;
    public static final String[] CHANNEL_NAMES = {"1", "2", "MASTER"};

    public static String tipKey(int type) {
        return "gui.soundsystem_dj.beatfx." + NAMES[type].toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }
}
