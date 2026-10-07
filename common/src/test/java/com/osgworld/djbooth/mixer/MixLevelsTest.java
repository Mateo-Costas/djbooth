package com.osgworld.djbooth.mixer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sums that decide whether anyone actually hears a deck. */
class MixLevelsTest {

    private static final int LIN = MixLevels.CURVE_LINEAR;
    private static final int SLOW = MixLevels.XF_CURVE_SLOW;
    private static final int MED = MixLevels.XF_CURVE_MEDIUM;
    private static final int FAST = MixLevels.XF_CURVE_FAST;
    private static final int[] XF_CURVES = {SLOW, MED, FAST};
    private static final int[] FADER_CURVES = {MixLevels.CURVE_SLOW, LIN, MixLevels.CURVE_SHARP};

    // --- Channel fader curves ---------------------------------------------------------------------

    @Test
    void everyCurveAgreesAtBothEnds() {
        // Whichever curve is selected, all the way down is silence and all the way up is unity.
        // Only the shape in between differs, which is the whole point of having three of them.
        for (int shape : FADER_CURVES) {
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
    void theCurvesAreShapedHardEnoughToBeWorthHaving() {
        // The ordering above is too weak on its own: flattening SHARP from a cube to a square
        // keeps it below linear and passes, while audibly changing what the fader does. A cut
        // curve has to be genuinely shut at halfway to be a cut curve, and a blend curve
        // genuinely open, so pin how far each one has to go rather than only which side it is on.
        float sharp = MixLevels.curve(0.5f, MixLevels.CURVE_SHARP);
        float slow = MixLevels.curve(0.5f, MixLevels.CURVE_SLOW);
        assertTrue(sharp <= 0.15f,
                "SHARP must be nearly shut at halfway to cut, was " + sharp);
        assertTrue(slow >= 0.6f,
                "SLOW must be well open at halfway to blend, was " + slow);
        // And SHARP has to keep holding back near the top, which is where a square gives up.
        assertTrue(MixLevels.curve(0.8f, MixLevels.CURVE_SHARP) <= 0.55f,
                "SHARP should still be held back at 80%");
    }

    @Test
    void curvesRiseAllTheWayUpWithNoDeadSpots() {
        for (int shape : FADER_CURVES) {
            float previous = -1;
            for (int i = 0; i <= 100; i++) {
                float v = MixLevels.curve(i / 100f, shape);
                assertTrue(v >= previous, "curve " + shape + " dipped at " + i + "%");
                previous = v;
            }
        }
    }

    // --- Crossfader curves ------------------------------------------------------------------------
    // Pioneer publishes the shapes only as descriptions ("gradual" ... "rising quickly"), so these
    // tests pin properties of those descriptions, not the exact numbers.

    @Test
    void everyCrossfaderCurveIsFullAtItsOwnEndAndSilentAtTheOther() {
        for (int curve : XF_CURVES) {
            assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_A, 0f, curve), 1e-6, "A at A, curve " + curve);
            assertEquals(0f, MixLevels.crossfaderWeight(MixLevels.XF_B, 0f, curve), 1e-6, "B at A, curve " + curve);
            assertEquals(0f, MixLevels.crossfaderWeight(MixLevels.XF_A, 1f, curve), 1e-6, "A at B, curve " + curve);
            assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_B, 1f, curve), 1e-6, "B at B, curve " + curve);
        }
    }

    @Test
    void slowIsAConstantPowerBlend() {
        // The manual: the gradual curve brings B up while A comes down. Constant power is the classic
        // way to do that (Rane's Figure 1): the two powers always add to one, so the room does not
        // dip or surge as the fader passes through the middle.
        for (int i = 0; i <= 100; i++) {
            float xf = i / 100f;
            float a = MixLevels.crossfaderWeight(MixLevels.XF_A, xf, SLOW);
            float b = MixLevels.crossfaderWeight(MixLevels.XF_B, xf, SLOW);
            assertEquals(1.0, a * a + b * b, 1e-5, "powers should sum to one at " + i + "%");
        }
        float centre = MixLevels.crossfaderWeight(MixLevels.XF_A, 0.5f, SLOW);
        assertEquals(-3.01, 20 * Math.log10(centre), 0.02, "each side is 3 dB down at the centre");
    }

    @Test
    void fastKeepsBothSidesAtFullAcrossTheMiddle() {
        // The manual: with the fast curve, as soon as the fader leaves A the sound comes from B. The
        // way a cut curve does that is by leaving both sides at full for almost the whole throw.
        for (int i = 10; i <= 90; i++) {
            float xf = i / 100f;
            assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_A, xf, FAST), 1e-6, "A at " + i + "%");
            assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_B, xf, FAST), 1e-6, "B at " + i + "%");
        }
        // ... and B is already half open a hair from the A end.
        assertTrue(MixLevels.crossfaderWeight(MixLevels.XF_B, 0.03f, FAST) >= 0.45f,
                "FAST should open almost immediately");
    }

    @Test
    void mediumSitsBetweenSlowAndFastAtEveryPoint() {
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            float slow = MixLevels.crossfaderSide(p, SLOW);
            float med = MixLevels.crossfaderSide(p, MED);
            float fast = MixLevels.crossfaderSide(p, FAST);
            assertTrue(slow <= med + 1e-6f && med <= fast + 1e-6f,
                    "ordering broken at " + i + "%: " + slow + " " + med + " " + fast);
        }
    }

    @Test
    void everyCrossfaderCurveOnlyEverRisesTowardItsOwnSide() {
        for (int curve : XF_CURVES) {
            float previous = -1;
            for (int i = 0; i <= 200; i++) {
                float v = MixLevels.crossfaderSide(i / 200f, curve);
                assertTrue(v >= previous - 1e-6f, "curve " + curve + " dipped at " + i);
                assertTrue(v >= 0f && v <= 1f, "curve " + curve + " out of range: " + v);
                previous = v;
            }
        }
    }

    @Test
    void thruTakesAChannelOffTheCrossfaderEntirely() {
        // The point of THRU: the crossfader can be anywhere and the channel still plays.
        for (int curve : XF_CURVES) {
            for (float xf = 0f; xf <= 1f; xf += 0.1f) {
                assertEquals(1f, MixLevels.crossfaderWeight(MixLevels.XF_THRU, xf, curve), 1e-6,
                        "THRU should ignore the crossfader at " + xf);
            }
        }
    }

    // --- Putting the gains together ---------------------------------------------------------------

    @Test
    void aClosedFaderIsSilentNoMatterWhatElseIsOpen() {
        assertEquals(0f, MixLevels.channelVolume(0f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f), 1e-6);
        // ... and so is a closed master.
        assertEquals(0f, MixLevels.channelVolume(1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 0f), 1e-6);
    }

    @Test
    void everythingOpenIsUnityAndNothingEverExceedsIt() {
        assertEquals(1f, MixLevels.channelVolume(1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f), 1e-6);
        for (int curve : XF_CURVES) {
            for (float fader = 0; fader <= 1f; fader += 0.25f) {
                for (float xf = 0; xf <= 1f; xf += 0.25f) {
                    for (float master = 0; master <= 1f; master += 0.25f) {
                        float v = MixLevels.channelVolume(fader, LIN, MixLevels.XF_A, xf, curve, master);
                        assertTrue(v >= 0f && v <= 1f, "volume out of range: " + v);
                    }
                }
            }
        }
    }

    @Test
    void splitGainsMultiplyToTheVolumeTheChannelAlwaysHad() {
        // Moving the fader ahead of the effect must not change how loud normal mixing is: whichever
        // way the effect is patched, the part before it times the part after it is the old volume.
        float[] positions = {0f, 0.2f, 0.5f, 0.8f, 1f};
        for (boolean effectOnMaster : new boolean[]{false, true}) {
            for (int fc : FADER_CURVES) {
                for (int xc : XF_CURVES) {
                    for (int assign : new int[]{MixLevels.XF_A, MixLevels.XF_THRU, MixLevels.XF_B}) {
                        for (float fader : positions) {
                            for (float xf : positions) {
                                for (float master : positions) {
                                    MixLevels.Gains g = MixLevels.gains(effectOnMaster, fader, fc, assign,
                                            xf, xc, master, false, 1f, false, false);
                                    float expected = MixLevels.channelVolume(fader, fc, assign, xf, xc, master);
                                    assertEquals(expected, g.total(), 1e-6,
                                            "fader " + fader + " xf " + xf + " master " + master
                                                    + " master-fx " + effectOnMaster);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void aBeatFxOnTheChannelComesAfterTheFaderButAheadOfTheCrossfader() {
        // Manual: lowering the faders leaves the effect ringing. And the crossfader has its own
        // CROSS FADER A / B effect targets, which only makes sense if a channel effect sits before it.
        MixLevels.Gains g = MixLevels.gains(false, 0.4f, LIN, MixLevels.XF_A, 0.25f, SLOW, 0.5f,
                false, 1f, false, false);
        assertEquals(0.4f, g.pre(), 1e-6, "ahead of the effect: only the channel fader");
        assertEquals(MixLevels.crossfaderWeight(MixLevels.XF_A, 0.25f, SLOW) * 0.5f, g.post(), 1e-6,
                "after the effect: the crossfader and master");
    }

    @Test
    void aBeatFxOnMasterComesAfterTheWholeMix() {
        MixLevels.Gains g = MixLevels.gains(true, 0.4f, LIN, MixLevels.XF_A, 0.25f, SLOW, 0.5f,
                false, 1f, false, false);
        assertEquals(0.4f * MixLevels.crossfaderWeight(MixLevels.XF_A, 0.25f, SLOW), g.pre(), 1e-6,
                "ahead of a master effect: fader and crossfader");
        assertEquals(0.5f, g.post(), 1e-6, "after it: only master");
    }

    @Test
    void closingTheFaderLeavesTheEffectsInputSilentButTheMasterOpen() {
        // The arrangement echo-out relies on: the effect is fed nothing, but nothing downstream of
        // it has been closed, so its tail still reaches the speakers.
        MixLevels.Gains g = MixLevels.gains(false, 0f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f,
                false, 1f, false, false);
        assertEquals(0f, g.pre(), 1e-6);
        assertEquals(1f, g.post(), 1e-6);
    }

    @Test
    void cueingAChannelPreviewsItAtTheBoothAndMutesTheRest() {
        // Deck A cued with its fader all the way down: CUE listens before the fader, so the booth
        // still hears it at the booth level. Deck B is not cued and drops out of the booth feed.
        MixLevels.Gains cued = MixLevels.gains(false, 0f, LIN, MixLevels.XF_A, 1f, SLOW, 0.3f,
                true, 0.8f, true, true);
        assertEquals(1f, cued.pre(), 1e-6, "a cued channel is heard before its fader");
        assertEquals(0.8f, cued.post(), 1e-6);
        assertEquals(0.8f, cued.total(), 1e-6);

        MixLevels.Gains other = MixLevels.gains(false, 1f, LIN, MixLevels.XF_B, 1f, SLOW, 1f,
                true, 0.8f, true, false);
        assertEquals(0f, other.total(), 1e-6, "an uncued channel drops out of the booth feed");
    }

    @Test
    void withNothingCuedTheBoothHearsTheMixAtItsOwnLevel() {
        MixLevels.Gains g = MixLevels.gains(false, 1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f,
                true, 0.5f, false, false);
        assertEquals(0.5f, g.total(), 1e-6);
    }

    @Test
    void theBoothIsNotScaledByMasterLevel() {
        // BALANCE acts on BOOTH as well as MASTER, but MASTER LEVEL acts only on the MASTER outputs,
        // so turning the room down must not turn the DJ's monitor down with it.
        MixLevels.Gains booth = MixLevels.gains(false, 1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 0f,
                true, 0.7f, false, false);
        assertEquals(0.7f, booth.total(), 1e-6, "master at zero, booth still audible");
        MixLevels.Gains floor = MixLevels.gains(false, 1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 0f,
                false, 0.7f, false, false);
        assertEquals(0f, floor.total(), 1e-6, "master at zero silences the floor");
    }

    @Test
    void boothLevelStillMutesTheBooth() {
        assertEquals(0f, MixLevels.gains(false, 1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f,
                true, 0f, false, false).total(), 1e-6);
        assertEquals(0f, MixLevels.gains(false, 1f, LIN, MixLevels.XF_THRU, 0.5f, SLOW, 1f,
                true, 0f, true, true).total(), 1e-6);
    }

    @Test
    void notANumberNeverGetsThroughAsALevel() {
        MixLevels.Gains g = MixLevels.gains(false, Float.NaN, LIN, MixLevels.XF_A, Float.NaN, FAST,
                Float.NaN, false, Float.NaN, false, false);
        assertTrue(g.pre() >= 0f && g.pre() <= 1f && g.post() >= 0f && g.post() <= 1f,
                "gains must stay finite and in range: " + g);
    }
}
