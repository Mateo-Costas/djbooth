package com.osgworld.djbooth.client.screen.widget;

/**
 * The arithmetic behind the panel controls: what a drag, a wheel notch or a click on a fader is
 * worth, and where a fader's cap is drawn.
 *
 * <p>Separate from the widgets because they cannot be tested. Reading the shift key goes through
 * {@code Screen.hasShiftDown()}, which reaches into a live {@code Minecraft} instance and throws
 * without a running game, so every path that asks about fine mode is unreachable from a test. The
 * widgets now read the key and pass the answer in; everything that decides a value lives here,
 * where it can be checked exhaustively.
 */
public final class PanelMath {
    private PanelMath() {}

    /** Pixels of vertical drag for one full turn of a knob. */
    public static final double KNOB_DRAG_PIXELS = 150.0;
    /** One notch of the mouse wheel, as a fraction of a control's travel. */
    public static final double WHEEL_STEP = 0.04;
    /** What holding shift multiplies a movement by. */
    public static final double FINE = 0.25;
    /** Two clicks closer together than this are a double click. */
    public static final long DOUBLE_CLICK_MS = 300;
    /** Height (or width) of the cap drawn on a fader. */
    public static final int FADER_CAP_PX = 6;

    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * Where a knob lands after dragging {@code dragY} pixels from {@code current}.
     *
     * <p>Dragging up raises the value, which is why the pixels are subtracted: screen Y grows
     * downward.
     */
    public static double knobAfterDrag(double current, double dragY, boolean fine) {
        double pixelsPerTurn = KNOB_DRAG_PIXELS / (fine ? FINE : 1.0);
        return clamp01(current - dragY / pixelsPerTurn);
    }

    /** Where a control lands after {@code notches} of wheel from {@code current}. */
    public static double afterWheel(double current, double notches, boolean fine) {
        return clamp01(current + notches * WHEEL_STEP * (fine ? FINE : 1.0));
    }

    /**
     * The value a fader should take when the pointer is at {@code mx, my}.
     *
     * <p>Measured over the travel the cap actually moves across, not the whole widget. Mapping over
     * the full height while drawing the cap over {@code height - 6} meant the two disagreed by half
     * a cap at each end: grabbing a fader parked at the top made it jump down 3% before it started
     * following the mouse. Pairing this with {@link #faderCapOffset} keeps the pixel you grab and
     * the value you get in agreement everywhere.
     */
    public static double faderValueAt(boolean vertical, int x, int y, int w, int h,
                                      double mx, double my) {
        double travel = Math.max(1, (vertical ? h : w) - FADER_CAP_PX);
        double half = FADER_CAP_PX / 2.0;
        double v = vertical
                ? (y + h - half - my) / travel
                : (mx - x - half) / travel;
        return clamp01(v);
    }

    /** How far along its track a fader's cap starts, for a value of {@code v}. */
    public static int faderCapOffset(boolean vertical, int w, int h, double v) {
        double travel = Math.max(1, (vertical ? h : w) - FADER_CAP_PX);
        double along = vertical ? (1.0 - clamp01(v)) : clamp01(v);
        return (int) Math.round(along * travel);
    }

    /** Angle of a point about a widget's centre, in degrees. */
    public static double angleAt(int x, int y, int w, int h, double mx, double my) {
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        return Math.toDegrees(Math.atan2(my - cy, mx - cx));
    }

    /**
     * The shortest way round from one angle to another, in degrees.
     *
     * <p>Without this a jog dragged past the top would read as a 359 degree lurch backwards
     * instead of one degree forwards.
     */
    public static double angleDelta(double from, double to) {
        double d = to - from;
        while (d > 180) {
            d -= 360;
        }
        while (d < -180) {
            d += 360;
        }
        return d;
    }

    /** Whether a click at {@code nowMs} follows one at {@code lastMs} closely enough to pair. */
    public static boolean isDoubleClick(long lastMs, long nowMs) {
        return lastMs != 0 && nowMs - lastMs <= DOUBLE_CLICK_MS;
    }
}
