package com.osgworld.djbooth.client.screen.widget;

import com.osgworld.djbooth.mixer.MixLevels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry behind the mixer's small switches. The switches are about thirteen pixels wide, so
 * what matters is that nothing is drawn outside the box (it would paint over the art around it) and
 * that a label shrinks to fit instead of being clipped.
 */
class PanelSwitchTest {

    // The size the switches have at the default GUI scale in a 1600 x 900 window.
    private static final int W = 13;
    private static final int H = 8;

    @Test
    void aLongLabelShrinksToFitInsteadOfClipping() {
        // "SHARP" is 29 px wide at full size in Minecraft's font; the box is 13.
        float s = PanelSwitch.fitScale(29, 9, W, H);
        assertTrue(29 * s <= W - 2 + 1e-3, "text is " + (29 * s) + " px in a box " + W + " wide");
        assertTrue(9 * s <= H, "text is " + (9 * s) + " px tall in a box " + H);
    }

    @Test
    void aShortLabelIsNotBlownUp() {
        // A one-letter label in a big box must stay at the size the panel prints, not fill the box.
        assertEquals(0.7f, PanelSwitch.fitScale(6, 9, 60, 30), 1e-6f);
    }

    @Test
    void aDegenerateBoxStillGivesAUsableScale() {
        float s = PanelSwitch.fitScale(100, 9, 1, 1);
        assertTrue(s > 0f && s <= 0.7f, "scale " + s);
    }

    @Test
    void everyCurvePointStaysInsideTheBox() {
        for (int dim : new int[]{1, 2}) {
            for (PanelSwitch.CurveShape shape : new PanelSwitch.CurveShape[]{
                    PanelSwitch.channelFader(), PanelSwitch.crossfader()}) {
                for (int state = 0; state < 3; state++) {
                    for (int[] p : PanelSwitch.curvePoints(shape, state, W, H, dim)) {
                        assertTrue(p[0] >= 1 && p[0] <= W - 2, "column " + p[0] + " outside the box");
                        assertTrue(p[1] >= 1 && p[1] <= H - 2, "row " + p[1] + " outside the box");
                    }
                }
            }
        }
    }

    @Test
    void theRisingCurveClimbsLeftToRight() {
        // Screen rows grow downward, so a rising curve's row number only ever falls.
        for (int state = 0; state < 3; state++) {
            int[][] pts = PanelSwitch.curvePoints(PanelSwitch.channelFader(), state, 30, 14, 1);
            int previous = Integer.MAX_VALUE;
            for (int[] p : pts) {
                assertTrue(p[1] <= previous, "curve " + state + " fell at column " + p[0]);
                previous = p[1];
            }
        }
    }

    @Test
    void theThreeChannelCurvesLookDifferentFromEachOther() {
        // If two positions drew the same picture the switch would not show where it is set.
        int[][] slow = PanelSwitch.curvePoints(PanelSwitch.channelFader(), MixLevels.CURVE_SLOW, 30, 14, 1);
        int[][] lin = PanelSwitch.curvePoints(PanelSwitch.channelFader(), MixLevels.CURVE_LINEAR, 30, 14, 1);
        int[][] sharp = PanelSwitch.curvePoints(PanelSwitch.channelFader(), MixLevels.CURVE_SHARP, 30, 14, 1);
        assertTrue(differ(slow, lin) && differ(lin, sharp) && differ(slow, sharp));
    }

    @Test
    void theThreeCrossfaderCurvesLookDifferentFromEachOther() {
        int[][] slow = PanelSwitch.curvePoints(PanelSwitch.crossfader(), MixLevels.XF_CURVE_SLOW, 30, 14, 2);
        int[][] med = PanelSwitch.curvePoints(PanelSwitch.crossfader(), MixLevels.XF_CURVE_MEDIUM, 30, 14, 2);
        int[][] fast = PanelSwitch.curvePoints(PanelSwitch.crossfader(), MixLevels.XF_CURVE_FAST, 30, 14, 2);
        assertTrue(differ(slow, med) && differ(med, fast) && differ(slow, fast));
    }

    @Test
    void aCutCurveShowsBothSidesFullAcrossTheMiddle() {
        // The point of drawing the mirror image: on FAST both lines sit on the top row in the middle.
        int w = 30, h = 14;
        int[][] pts = PanelSwitch.curvePoints(PanelSwitch.crossfader(), MixLevels.XF_CURVE_FAST, w, h, 2);
        int cols = w - 2;
        int mid = cols / 2;
        assertEquals(1, pts[mid][1], "the rising side should be at the top in the middle");
        assertEquals(1, pts[cols + mid][1], "and so should its mirror");
        // On the smooth curve they cross lower down, at the 3 dB point.
        int[][] smooth = PanelSwitch.curvePoints(PanelSwitch.crossfader(), MixLevels.XF_CURVE_SLOW, w, h, 2);
        assertTrue(smooth[mid][1] > 1, "SLOW should not be at full in the middle");
    }

    private static boolean differ(int[][] a, int[][] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i][1] != b[i][1]) {
                return true;
            }
        }
        return false;
    }
}
