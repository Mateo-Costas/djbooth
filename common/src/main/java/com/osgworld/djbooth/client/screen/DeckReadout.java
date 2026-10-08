package com.osgworld.djbooth.client.screen;

import java.util.Locale;

/**
 * Text for a deck's screen that is worked out rather than drawn, kept apart from the screen so it
 * can be tested without a running game.
 */
public final class DeckReadout {
    private DeckReadout() {}

    /** A tempo this close to 100% is not worth printing: it is the fader sitting at its centre. */
    private static final double HIDE_BELOW = 0.0005;

    /**
     * The tempo fader's position as a signed percentage, the way a CDJ prints it: {@code +4.0%} for
     * a track running 4% fast. Empty when the deck is at its own speed.
     *
     * <p>Locale-independent on purpose: a decimal comma beside a BPM that has a decimal point would
     * read as two different numbers.
     */
    public static String tempoText(double rate) {
        double change = (rate - 1.0) * 100.0;
        if (!Double.isFinite(change) || Math.abs(rate - 1.0) < HIDE_BELOW) {
            return "";
        }
        return String.format(Locale.ROOT, "%+.1f%%", change);
    }
}
