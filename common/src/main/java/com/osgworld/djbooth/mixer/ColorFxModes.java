package com.osgworld.djbooth.mixer;

/**
 * The six SOUND COLOR FX the DJM-900NXS2 offers, in the order the buttons sit on the panel.
 *
 * <p>These live away from the DSP so the mixer block entity, the network layer and the GUI can all
 * talk about a mode without dragging in the client-only audio code.
 */
public final class ColorFxModes {
    private ColorFxModes() {}

    public static final int SPACE = 0;
    public static final int DUB_ECHO = 1;
    public static final int SWEEP = 2;
    public static final int NOISE = 3;
    public static final int CRUSH = 4;
    public static final int FILTER = 5;
    public static final int MODES = 6;

    /** Panel names, indexed by mode. */
    public static final String[] NAMES =
            {"SPACE", "DUB ECHO", "SWEEP", "NOISE", "CRUSH", "FILTER"};

    /** Translation key for a mode's tooltip, e.g. {@code gui.djbooth.color.space}. */
    public static String tipKey(int mode) {
        return switch (mode) {
            case SPACE -> "gui.djbooth.color.space";
            case DUB_ECHO -> "gui.djbooth.color.dub_echo";
            case SWEEP -> "gui.djbooth.color.sweep";
            case NOISE -> "gui.djbooth.color.noise";
            case CRUSH -> "gui.djbooth.color.crush";
            default -> "gui.djbooth.color.filter";
        };
    }
}
