package com.osgworld.djbooth.client.screen;

/**
 * How a CDJ screen divides up: a header row, the zoomed waveform, and the overview strip along the
 * bottom.
 *
 * <p>One place, because the split is needed twice — once to draw the strip and once to work out
 * whether a click landed on it. Two copies of the same arithmetic drift, and when they do the
 * player clicks the strip and the deck jumps to a different point than the one under the pointer.
 * The same fault, in a smaller form, is why grabbing a fader used to make it hop.
 */
public record DeckScreenLayout(int headerX, int headerY, int headerW, int headerH,
                               int waveX, int waveY, int waveW, int waveH,
                               int overviewX, int overviewY, int overviewW, int overviewH) {

    /** Border between the screen's edge and its contents. */
    public static final int PAD = 2;
    /** Gap between the waveform and the strip under it. */
    public static final int GAP = 2;

    /**
     * Divide a screen rectangle.
     *
     * @param lineHeight the font's line height, which sets the header row
     */
    public static DeckScreenLayout of(int x, int y, int w, int h, int lineHeight) {
        int innerX = x + PAD;
        int innerW = Math.max(1, w - PAD * 2);
        int headerH = lineHeight + 1;
        int overviewH = Math.max(5, h / 7);
        // The waveform takes what is left. On a screen too short to hold everything it collapses to
        // a minimum rather than going negative, which would draw inside out.
        int waveH = Math.max(6, h - PAD * 2 - headerH - overviewH - GAP);
        int headerY = y + PAD;
        int waveY = headerY + headerH;
        int overviewY = waveY + waveH + GAP;
        return new DeckScreenLayout(
                innerX, headerY, innerW, headerH,
                innerX, waveY, innerW, waveH,
                innerX, overviewY, innerW, overviewH);
    }

    /** Whether a point is on the overview strip, with a little slack for a moving mouse. */
    public boolean overviewHit(double mx, double my, int grab) {
        return mx >= overviewX && mx <= overviewX + overviewW
                && my >= overviewY - grab && my <= overviewY + overviewH + grab;
    }

    /** How far along the track a click at {@code mx} points, 0..1. */
    public double overviewFraction(double mx) {
        double f = (mx - overviewX) / Math.max(1, overviewW);
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }
}
