package com.osgworld.djbooth.mixer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sums that decide whether anyone actually hears a deck. */
class MixLevelsTest {

    private static final int LIN = MixLevels.CURVE_LINEAR;

    @Test
    void everyCurveAgreesAtBothEnds() {
        // Whichever curve is selected, all the way down is silence and all the way up is unity.
        // Only the shape in between differs, which is the whole point of having three of them.
        for (int shape : new int[]{MixLevels.CURVE_SLOW, LIN, MixLevels.CURVE_SHARP}) {
            assertEquals(0f, MixLevels.curve(0f, shape), 1e-6, "curve " + shape + " at the bottom");
            assertEquals(1f, MixLevels.curve(1f, shape), 1e-6, "curve " + shape + " at the top");
        }
    }

    @Test
    void sharpStaysQuietLongerAndSlowOpensUpEarlier() {
        float half = 0.5f;
        float sharp = MixLevels.curve(half, MixLevels.CURVE_SHARP);
        float linear = MixLevels.curve(half, LIN);
        float slow = MixLevels.curve(half, MixLevels.CURVE_SLOW);
        assertTrue(sharp < linear, "SHARP should still be quiet at halfway, was " + sharp);
        assertTrue(slow > linear, "SLOW should already be open at halfway, was " + slow);
    }

    @Test
    void curvesRiseAllTheWayUpWithNoDeadSpots() {
        for (int shape : new int[]{MixLevels.CURVE_SLOW, LIN, MixLevels.CURVE_SHARP}) {
            float previous = -1;
            for (int i = 0; i <= 100; i++) {
                float v = MixLevels.curve(i / 100f, shape);
                assertTrue(v >= previous, "curve " + shape + " dipped at " + i + "%");
                previous = v;
            }
        }
    }

    @Test
    void crossfaderFavoursWhicheverSideItIsPushedTo() {
        // Hard left: side A at full, side B silent.
        assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_A, 0f, LIN), 1e-6);
        assertEquals(0f, MixLevels.crossfaderWeight(MixLevels.XF_B, 0f, LIN), 1e-6);
        // Hard right: the other way round.
        assertEquals(0f, MixLevels.crossfaderWeight(MixLevels.XF_A, 1f, LIN), 1e-6);
        assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_B, 1f, LIN), 1e-6);
        // Centred: both halfway, so a straight blend.
        assertEquals(0.5f, MixLevels.crossfaderWeight(MixLevels.XF_A, 0.5f, LIN), 1e-6);
        assertEquals(0.5f, MixLevels.crossfaderWeight(MixLevels.XF_B, 0.5f, LIN), 1e-6);
    }

    @Test
    void thruTakesAChannelOffTheCrossfaderEntirely() {
        // The point of THRU: the crossfader can be anywhere and the channel still plays.
        for (float xf = 0f; xf <= 1f; xf += 0.1f) {
            assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_THRU, xf, LIN), 1e-6,
                    "THRU should ignore the crossfader at " + xf);
        }
    }

    @Test
    void aClosedFaderIsSilentNoMatterWhatElseIsOpen() {
        assertEquals(0f, MixLevels.channelVolume(0f, LIN, MixLevels.XF_THRU, 0.5f, LIN, 1f), 1e-6);
        // ... and so is a closed master.
        assertEquals(0f, MixLevels.channelVolume(1f, LIN, MixLevels.XF_THRU, 0.5f, LIN, 0f), 1e-6);
    }

    @Test
    void everythingOpenIsUnityAndNothingEverExceedsIt() {
        assertEquals(1f, MixLevels.channelVolume(1f, LIN, MixLevels.XF_THRU, 0.5f, LIN, 1f), 1e-6);
        for (float fader = 0; fader <= 1f; fader += 0.25f) {
            for (float xf = 0; xf <= 1f; xf += 0.25f) {
                for (float master = 0; master <= 1f; master += 0.25f) {
                    float v = MixLevels.channelVolume(fader, LIN, MixLevels.XF_A, xf, LIN, master);
                    assertTrue(v >= 0f && v <= 1f, "volume out of range: " + v);
                }
            }
        }
    }

    @Test
    void cueingAChannelPreviewsItAtTheBoothAndMutesTheRest() {
        // Nothing cued: the booth just hears the floor mix, scaled by its own knob.
        assertEquals(0.5f, MixLevels.boothVolume(1f, 0.5f, false, false), 1e-6);

        // Deck A cued: the booth hears A at the booth level and B not at all, while the floor mix
        // (passed in as floorVolume) is untouched for everyone else.
        assertEquals(0.8f, MixLevels.boothVolume(0f, 0.8f, true, true), 1e-6,
                "a cued channel is audible at the booth even with its fader down");
        assertEquals(0f, MixLevels.boothVolume(1f, 0.8f, true, false), 1e-6,
                "an uncued channel drops out of the booth feed");
    }

    @Test
    void boothLevelStillMutesTheBooth() {
        assertEquals(0f, MixLevels.boothVolume(1f, 0f, false, false), 1e-6);
        assertEquals(0f, MixLevels.boothVolume(1f, 0f, true, true), 1e-6);
    }
}
