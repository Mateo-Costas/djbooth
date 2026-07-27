package com.osgworld.djbooth.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The CDJ screen's three rows, at every size the panel can be scaled to.
 *
 * <p>This exists because the strip is measured twice: once to draw it, once to decide whether a
 * click landed on it. The GUI rescales with the window, so the screen rectangle is whatever the
 * panel works out at that resolution, not a fixed size. Both users of the split now come from here,
 * and what follows checks the split is sane at any of them.
 */
class DeckScreenLayoutTest {

    /** Screen sizes from a tiny window up to a large one, plus deliberately awkward ones. */
    private static int[][] sizes() {
        return new int[][] {
            {8, 6}, {20, 14}, {40, 22}, {80, 34}, {120, 48}, {240, 90}, {600, 220},
            {31, 17}, {97, 41}, // odd numbers, to catch integer division landing badly
        };
    }

    @Test
    void theThreeRowsStackWithoutOverlapping() {
        for (int[] s : sizes()) {
            for (int lineHeight : new int[] {7, 9, 12}) {
                var l = DeckScreenLayout.of(10, 20, s[0], s[1], lineHeight);
                assertTrue(l.headerY() + l.headerH() <= l.waveY(),
                        "header overlaps the waveform at " + s[0] + "x" + s[1]);
                assertTrue(l.waveY() + l.waveH() <= l.overviewY(),
                        "waveform overlaps the overview at " + s[0] + "x" + s[1]);
            }
        }
    }

    @Test
    void everyRowHasRealSize() {
        // A negative or zero height draws inside out, or not at all, with no error anywhere.
        for (int[] s : sizes()) {
            for (int lineHeight : new int[] {7, 9, 12}) {
                var l = DeckScreenLayout.of(0, 0, s[0], s[1], lineHeight);
                assertTrue(l.headerH() > 0, "header collapsed at " + s[0] + "x" + s[1]);
                assertTrue(l.waveH() > 0, "waveform collapsed at " + s[0] + "x" + s[1]);
                assertTrue(l.overviewH() > 0, "overview collapsed at " + s[0] + "x" + s[1]);
                assertTrue(l.waveW() > 0 && l.overviewW() > 0 && l.headerW() > 0,
                        "a row has no width at " + s[0] + "x" + s[1]);
            }
        }
    }

    @Test
    void theRowsShareOneLeftEdgeAndWidth() {
        for (int[] s : sizes()) {
            var l = DeckScreenLayout.of(37, 11, s[0], s[1], 9);
            assertEquals(l.headerX(), l.waveX(), "rows are not aligned");
            assertEquals(l.waveX(), l.overviewX(), "rows are not aligned");
            assertEquals(l.headerW(), l.waveW(), "rows are different widths");
            assertEquals(l.waveW(), l.overviewW(), "rows are different widths");
        }
    }

    @Test
    void everythingStaysInsideTheScreen() {
        for (int[] s : sizes()) {
            int x = 5, y = 9;
            var l = DeckScreenLayout.of(x, y, s[0], s[1], 9);
            assertTrue(l.headerX() >= x, "content starts left of the screen");
            assertTrue(l.headerX() + l.headerW() <= x + s[0] + 1, "content runs off the right");
            assertTrue(l.headerY() >= y, "content starts above the screen");
            // The bottom is allowed to reach the edge exactly on the smallest sizes, where the
            // minimums win over the available height.
            if (s[1] >= 24) {
                assertTrue(l.overviewY() + l.overviewH() <= y + s[1],
                        "the overview runs off the bottom at height " + s[1]);
            }
        }
    }

    @Test
    void clickingTheStripHitsIt() {
        var l = DeckScreenLayout.of(100, 200, 160, 60, 9);
        assertTrue(l.overviewHit(l.overviewX(), l.overviewY(), 0), "the top-left corner missed");
        assertTrue(l.overviewHit(l.overviewX() + l.overviewW(),
                l.overviewY() + l.overviewH(), 0), "the bottom-right corner missed");
        assertTrue(l.overviewHit(l.overviewX() + l.overviewW() / 2.0,
                l.overviewY() + l.overviewH() / 2.0, 0), "the middle missed");
    }

    @Test
    void clickingTheWaveformDoesNotSeek() {
        // The zoomed wave is centred on the playhead, so a click there means nothing in absolute
        // time. Worse, it sits between the knobs: a stray click must not jump the deck mid-mix.
        var l = DeckScreenLayout.of(100, 200, 160, 60, 9);
        double waveMiddle = l.waveY() + l.waveH() / 2.0;
        assertFalse(l.overviewHit(l.waveX() + 10, waveMiddle, 2),
                "a click in the middle of the waveform was treated as a seek");
        assertFalse(l.overviewHit(l.headerX() + 10, l.headerY() + 1, 2),
                "a click on the header was treated as a seek");
    }

    @Test
    void theGrabSlackWorksBothWaysAndOnlyVertically() {
        var l = DeckScreenLayout.of(0, 0, 160, 60, 9);
        assertTrue(l.overviewHit(l.overviewX() + 5, l.overviewY() - 2, 2), "no slack above");
        assertTrue(l.overviewHit(l.overviewX() + 5, l.overviewY() + l.overviewH() + 2, 2),
                "no slack below");
        assertFalse(l.overviewHit(l.overviewX() - 1, l.overviewY() + 1, 2),
                "slack must not widen the strip sideways");
        assertFalse(l.overviewHit(l.overviewX() + l.overviewW() + 1, l.overviewY() + 1, 2),
                "slack must not widen the strip sideways");
    }

    @Test
    void aClickMapsToWhereItLandsAlongTheTrack() {
        var l = DeckScreenLayout.of(100, 200, 160, 60, 9);
        assertEquals(0.0, l.overviewFraction(l.overviewX()), 1e-9);
        assertEquals(1.0, l.overviewFraction(l.overviewX() + l.overviewW()), 1e-9);
        assertEquals(0.5, l.overviewFraction(l.overviewX() + l.overviewW() / 2.0), 0.01);
    }

    @Test
    void aClickOutsideTheStripClampsRatherThanRunningOffTheTrack() {
        var l = DeckScreenLayout.of(100, 200, 160, 60, 9);
        assertEquals(0.0, l.overviewFraction(-9999));
        assertEquals(1.0, l.overviewFraction(9999));
    }

    @Test
    void theFractionRisesSteadilyAcrossTheStrip() {
        var l = DeckScreenLayout.of(0, 0, 200, 70, 9);
        double previous = -1;
        for (double mx = l.overviewX(); mx <= l.overviewX() + l.overviewW(); mx += 0.5) {
            double f = l.overviewFraction(mx);
            assertTrue(f >= previous, "the seek fraction went backwards along the strip");
            previous = f;
        }
        assertEquals(1.0, previous, 1e-9);
    }

    @Test
    void aTallerFontPushesTheRowsDownWithoutBreakingThem() {
        var small = DeckScreenLayout.of(0, 0, 160, 60, 7);
        var big = DeckScreenLayout.of(0, 0, 160, 60, 14);
        assertTrue(big.waveY() > small.waveY(), "a taller font did not move the waveform down");
        assertTrue(big.waveH() <= small.waveH(), "a taller font gave the waveform more room");
        assertTrue(big.waveH() > 0 && big.overviewH() > 0, "a taller font collapsed a row");
    }
}
