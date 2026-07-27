package com.osgworld.djbooth.client.screen.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Every way a hand can move a control on the panel, checked value by value.
 *
 * <p>These are the controls that get touched most and were tested least. A knob that overshoots its
 * travel, a fine drag that is not actually finer, a fader that jumps when you grab it: none of it
 * crashes, so none of it shows up anywhere except in the feel of the thing.
 */
class PanelMathTest {

    // --- clamping -------------------------------------------------------------------------

    @Test
    void valuesNeverLeaveTheirTravel() {
        assertEquals(0.0, PanelMath.clamp01(-0.0001));
        assertEquals(0.0, PanelMath.clamp01(-100));
        assertEquals(1.0, PanelMath.clamp01(1.0001));
        assertEquals(1.0, PanelMath.clamp01(100));
        assertEquals(0.5, PanelMath.clamp01(0.5));
        assertEquals(0.0, PanelMath.clamp01(0.0));
        assertEquals(1.0, PanelMath.clamp01(1.0));
    }

    // --- knob: drag -----------------------------------------------------------------------

    @Test
    void draggingUpRaisesAndDownLowers() {
        // Screen Y grows downward, so a negative dragY is an upward drag.
        assertTrue(PanelMath.knobAfterDrag(0.5, -10, false) > 0.5, "dragging up must raise the knob");
        assertTrue(PanelMath.knobAfterDrag(0.5, 10, false) < 0.5, "dragging down must lower it");
    }

    @Test
    void aFullTurnTakesTheAdvertisedTravel() {
        // 150 px is one full 0..1 turn, so half of it from centre reaches an end exactly.
        assertEquals(1.0, PanelMath.knobAfterDrag(0.5, -PanelMath.KNOB_DRAG_PIXELS / 2, false), 1e-12);
        assertEquals(0.0, PanelMath.knobAfterDrag(0.5, PanelMath.KNOB_DRAG_PIXELS / 2, false), 1e-12);
    }

    @Test
    void shiftMakesADragExactlyFourTimesFiner() {
        double coarse = PanelMath.knobAfterDrag(0.5, -30, false) - 0.5;
        double fine = PanelMath.knobAfterDrag(0.5, -30, true) - 0.5;
        assertEquals(coarse * PanelMath.FINE, fine, 1e-12,
                "holding shift must scale a drag by FINE, not something else");
        assertTrue(fine < coarse, "fine mode must move less than coarse for the same drag");
    }

    @Test
    void aKnobCannotBeDraggedPastEitherEnd() {
        assertEquals(1.0, PanelMath.knobAfterDrag(0.99, -10000, false));
        assertEquals(0.0, PanelMath.knobAfterDrag(0.01, 10000, false));
        assertEquals(1.0, PanelMath.knobAfterDrag(1.0, -1, false));
        assertEquals(0.0, PanelMath.knobAfterDrag(0.0, 1, false));
    }

    @Test
    void draggingNowhereChangesNothing() {
        for (double v = 0; v <= 1.0001; v += 0.05) {
            assertEquals(PanelMath.clamp01(v), PanelMath.knobAfterDrag(v, 0, false), 1e-12);
            assertEquals(PanelMath.clamp01(v), PanelMath.knobAfterDrag(v, 0, true), 1e-12);
        }
    }

    @Test
    void dragIsReversibleAwayFromTheEnds() {
        // Push out and back by the same amount and land where you started, or the knob creeps.
        for (double v = 0.2; v <= 0.8; v += 0.1) {
            for (boolean fine : new boolean[] {false, true}) {
                double there = PanelMath.knobAfterDrag(v, -12, fine);
                double back = PanelMath.knobAfterDrag(there, 12, fine);
                assertEquals(v, back, 1e-12, "drag out and back drifted at v=" + v);
            }
        }
    }

    // --- wheel ----------------------------------------------------------------------------

    @Test
    void oneWheelNotchIsOneStep() {
        assertEquals(0.5 + PanelMath.WHEEL_STEP, PanelMath.afterWheel(0.5, 1, false), 1e-12);
        assertEquals(0.5 - PanelMath.WHEEL_STEP, PanelMath.afterWheel(0.5, -1, false), 1e-12);
    }

    @Test
    void shiftMakesAWheelNotchExactlyFourTimesFiner() {
        double coarse = PanelMath.afterWheel(0.5, 1, false) - 0.5;
        double fine = PanelMath.afterWheel(0.5, 1, true) - 0.5;
        assertEquals(coarse * PanelMath.FINE, fine, 1e-12);
    }

    @Test
    void severalNotchesAtOnceAddUp() {
        // Some mice report a multi-notch flick as one event.
        assertEquals(PanelMath.afterWheel(0.5, 3, false),
                PanelMath.afterWheel(PanelMath.afterWheel(PanelMath.afterWheel(0.5, 1, false),
                        1, false), 1, false), 1e-12);
    }

    @Test
    void theWheelCannotPushAControlPastEitherEnd() {
        assertEquals(1.0, PanelMath.afterWheel(0.99, 100, false));
        assertEquals(0.0, PanelMath.afterWheel(0.01, -100, false));
        assertEquals(1.0, PanelMath.afterWheel(1.0, 1, true));
        assertEquals(0.0, PanelMath.afterWheel(0.0, -1, true));
    }

    // --- fader ----------------------------------------------------------------------------

    @Test
    void aVerticalFaderReadsTopAsFullAndBottomAsZero() {
        int x = 40, y = 100, w = 12, h = 80;
        assertEquals(1.0, PanelMath.faderValueAt(true, x, y, w, h, x + 6, y), 1e-9);
        assertEquals(0.0, PanelMath.faderValueAt(true, x, y, w, h, x + 6, y + h), 1e-9);
    }

    @Test
    void aHorizontalFaderReadsLeftAsZeroAndRightAsFull() {
        int x = 40, y = 100, w = 80, h = 12;
        assertEquals(0.0, PanelMath.faderValueAt(false, x, y, w, h, x, y + 6), 1e-9);
        assertEquals(1.0, PanelMath.faderValueAt(false, x, y, w, h, x + w, y + 6), 1e-9);
    }

    @Test
    void grabbingAFaderWhereItIsDrawnDoesNotMoveIt() {
        // The regression this class was extracted for. The cap is drawn over height minus its own
        // size, but the value used to be read over the whole height, so the two disagreed by half a
        // cap at each end: a fader parked at the top jumped down as soon as you touched it.
        for (boolean vertical : new boolean[] {true, false}) {
            for (int size : new int[] {20, 41, 80, 200}) {
                int x = 30, y = 70;
                int w = vertical ? 12 : size;
                int h = vertical ? size : 12;
                // The cap can only be drawn on whole pixels, so a short fader genuinely cannot
                // represent every value: on a 14 px travel one pixel is already 0.071. Half a
                // pixel is therefore the tightest honest tolerance, and it is what rules out the
                // systematic half-a-cap offset this test exists to catch.
                double travel = Math.max(1, (vertical ? h : w) - PanelMath.FADER_CAP_PX);
                double tolerance = 0.5 / travel + 1e-9;
                for (double v = 0; v <= 1.0001; v += 0.05) {
                    double value = PanelMath.clamp01(v);
                    int along = PanelMath.faderCapOffset(vertical, w, h, value);
                    // Aim at the middle of the cap, which is where a hand grabs it.
                    double centre = along + PanelMath.FADER_CAP_PX / 2.0;
                    double mx = vertical ? x + w / 2.0 : x + centre;
                    double my = vertical ? y + centre : y + h / 2.0;
                    double read = PanelMath.faderValueAt(vertical, x, y, w, h, mx, my);
                    assertEquals(value, read, tolerance,
                            "grabbing the cap moved the fader: vertical=" + vertical + " size="
                                    + size + " value=" + value);
                }
            }
        }
    }

    @Test
    void theCapStaysInsideItsTrack() {
        for (boolean vertical : new boolean[] {true, false}) {
            for (int size : new int[] {10, 41, 80}) {
                int w = vertical ? 12 : size;
                int h = vertical ? size : 12;
                int travel = (vertical ? h : w) - PanelMath.FADER_CAP_PX;
                for (double v = -0.5; v <= 1.5; v += 0.05) {
                    int along = PanelMath.faderCapOffset(vertical, w, h, v);
                    assertTrue(along >= 0 && along <= Math.max(1, travel),
                            "cap drawn outside its track at v=" + v + " size=" + size);
                }
            }
        }
    }

    @Test
    void aFaderIsMonotonicAlongItsTrack() {
        int x = 10, y = 10, w = 12, h = 100;
        double previous = -1;
        for (double my = y + h; my >= y; my -= 1) {
            double v = PanelMath.faderValueAt(true, x, y, w, h, x + 6, my);
            assertTrue(v >= previous, "a vertical fader went backwards while the mouse went up");
            previous = v;
        }
    }

    @Test
    void aPointerOutsideTheTrackClampsRatherThanWrapping() {
        int x = 10, y = 10, w = 12, h = 100;
        assertEquals(1.0, PanelMath.faderValueAt(true, x, y, w, h, x + 6, y - 500));
        assertEquals(0.0, PanelMath.faderValueAt(true, x, y, w, h, x + 6, y + 500));
    }

    @Test
    void aOnePixelFaderDoesNotDivideByZero() {
        // Degenerate, but a layout change could produce it and a NaN here silently kills a control.
        for (int size : new int[] {0, 1, PanelMath.FADER_CAP_PX}) {
            double v = PanelMath.faderValueAt(true, 0, 0, 12, size, 6, 0);
            assertTrue(Double.isFinite(v), "a fader of height " + size + " produced " + v);
        }
    }

    // --- jog ------------------------------------------------------------------------------

    @Test
    void theJogMeasuresAngleFromItsOwnCentre() {
        int x = 0, y = 0, w = 100, h = 100; // centre at 50,50
        assertEquals(0.0, PanelMath.angleAt(x, y, w, h, 100, 50), 1e-9);   // due right
        assertEquals(90.0, PanelMath.angleAt(x, y, w, h, 50, 100), 1e-9);  // straight down
        assertEquals(-90.0, PanelMath.angleAt(x, y, w, h, 50, 0), 1e-9);   // straight up
    }

    @Test
    void theJogTakesTheShortWayRoundThroughTheTop() {
        // The wrap that matters: dragging across the 180 degree seam must read as a small nudge,
        // not a lurch most of the way round the platter.
        assertEquals(2.0, PanelMath.angleDelta(179, -179), 1e-9);
        assertEquals(-2.0, PanelMath.angleDelta(-179, 179), 1e-9);
        assertEquals(1.0, PanelMath.angleDelta(0, 1), 1e-9);
        assertEquals(-1.0, PanelMath.angleDelta(0, -1), 1e-9);
    }

    @Test
    void noJogMoveIsEverReportedAsMoreThanHalfATurn() {
        for (double from = -180; from <= 180; from += 3) {
            for (double to = -180; to <= 180; to += 3) {
                double d = PanelMath.angleDelta(from, to);
                assertTrue(Math.abs(d) <= 180.0000001,
                        "delta " + d + " from " + from + " to " + to + " is more than half a turn");
            }
        }
    }

    @Test
    void aFullCircleOfJogDragSumsToOneTurn() {
        // Walk the pointer right round the platter in small steps; the deltas must add up to 360,
        // which is what makes a continuous spin scrub a predictable distance.
        int x = 0, y = 0, w = 100, h = 100;
        double total = 0;
        double last = PanelMath.angleAt(x, y, w, h, 90, 50);
        for (int deg = 1; deg <= 360; deg++) {
            double rad = Math.toRadians(deg);
            double mx = 50 + 40 * Math.cos(rad);
            double my = 50 + 40 * Math.sin(rad);
            double a = PanelMath.angleAt(x, y, w, h, mx, my);
            total += PanelMath.angleDelta(last, a);
            last = a;
        }
        assertEquals(360.0, total, 1e-6, "a full spin did not sum to one turn");
    }

    // --- double click ---------------------------------------------------------------------

    @Test
    void twoQuickClicksPairAndTwoSlowOnesDoNot() {
        assertTrue(PanelMath.isDoubleClick(1000, 1000 + PanelMath.DOUBLE_CLICK_MS));
        assertTrue(PanelMath.isDoubleClick(1000, 1001));
        assertFalse(PanelMath.isDoubleClick(1000, 1000 + PanelMath.DOUBLE_CLICK_MS + 1));
        assertFalse(PanelMath.isDoubleClick(1000, 5000));
    }

    @Test
    void theFirstClickOfASessionIsNeverADoubleClick() {
        // A zero timestamp means nothing has been clicked yet. Treating it as a click at time zero
        // would make the very first click on a freshly opened panel reset the control under it.
        assertFalse(PanelMath.isDoubleClick(0, 50));
        assertFalse(PanelMath.isDoubleClick(0, 0));
    }
}
