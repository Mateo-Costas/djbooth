package com.osgworld.djbooth;

import com.osgworld.djbooth.client.audio.dsp.ChannelEq;
import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.mixer.MixLevels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Values the server accepts from clients, and the one slip switch that used to stay lit. Every
 * input here is something a modified client can send in a packet.
 */
class DeckStateHardeningTest {

    @Test
    void aNonNumericRateNeverReachesTheDeck() {
        DeckState s = new DeckState();
        s.setRate(Double.NaN);
        assertEquals(1.0, s.getRate());
        s.setRateAt(Double.POSITIVE_INFINITY, 0);
        assertEquals(1.0, s.getRate());
        s.setRateAt(Double.NEGATIVE_INFINITY, 0);
        assertEquals(1.0, s.getRate());
    }

    @Test
    void rateStaysWithinWhatTheAudioPathCanPlay() {
        DeckState s = new DeckState();
        s.setRate(0.0);
        assertEquals(DeckState.MIN_RATE, s.getRate());
        s.setRate(-3.0);
        assertEquals(DeckState.MIN_RATE, s.getRate());
        s.setRate(1e9);
        assertEquals(DeckState.MAX_RATE, s.getRate());
        s.setRate(1.16);
        assertEquals(1.16, s.getRate());
    }

    @Test
    void theWidestPanelRangeAndABeatSyncOfTwoAreStillReachable() {
        DeckState s = new DeckState();
        s.setRate(0.4);   // WIDE, pushed all the way
        assertEquals(0.4, s.getRate());
        s.setRate(1.6);
        assertEquals(1.6, s.getRate());
        s.setRate(2.0);   // half-tempo track synced to a full-tempo one
        assertEquals(2.0, s.getRate());
    }

    @Test
    void aHugeScrubPositionCannotOverflowThePositionMath() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        s.setOffsetMs(Long.MAX_VALUE);
        assertEquals(DeckState.MAX_POSITION_MS, s.getOffsetMs());
        // Playing for a minute on top of it must not wrap round to something negative.
        assertTrue(s.positionMsAt(60_000) >= DeckState.MAX_POSITION_MS);

        s.jumpTo(Long.MAX_VALUE, 0);
        assertEquals(DeckState.MAX_POSITION_MS, s.getOffsetMs());
        s.jumpTo(-5, 0);
        assertEquals(0, s.getOffsetMs());
    }

    @Test
    void slipRevLightsSlipAndLeavingPutsItBack() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        assertFalse(s.isSlip());

        s.cycleDirection(1000);                       // FWD -> REV
        assertEquals(DeckState.DIR_REV, s.getDirection());
        assertFalse(s.isSlip());

        s.cycleDirection(2000);                       // REV -> SLIP REV
        assertEquals(DeckState.DIR_SLIP_REV, s.getDirection());
        assertTrue(s.isSlip());
        assertTrue(s.isSlipping());

        s.cycleDirection(3000);                       // SLIP REV -> FWD
        assertEquals(DeckState.DIR_FWD, s.getDirection());
        assertFalse(s.isSlipping());
        assertFalse(s.isSlip(), "SLIP was never pressed, so it must not stay lit after SLIP REV");
    }

    @Test
    void aSlipTheDjSwitchedOnIsLeftOnAfterSlipRev() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        s.setSlip(true); // pressed by hand
        s.cycleDirection(1000);
        s.cycleDirection(2000);
        s.cycleDirection(3000);
        assertEquals(DeckState.DIR_FWD, s.getDirection());
        assertTrue(s.isSlip());
    }

    @Test
    void pressingSlipWhileInSlipRevMakesItTheDjsOwn() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        s.cycleDirection(1000);
        s.cycleDirection(2000);                       // SLIP REV, SLIP lit by the direction
        s.setSlip(true);                              // the DJ presses SLIP as well
        s.cycleDirection(3000);                       // back to FWD
        assertTrue(s.isSlip(), "the DJ pressed SLIP, so leaving SLIP REV must not turn it off");
    }

    @Test
    void cyclingDirectionDoesNotMoveThePlayhead() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        long before = s.positionMsAt(5000);
        s.cycleDirection(5000);
        assertEquals(before, s.positionMsAt(5000));
    }

    @Test
    void aNaNLevelIsSilenceNotPoison() {
        assertEquals(0f, MixLevels.clamp01(Float.NaN));
        assertEquals(0f, MixLevels.clamp01(Float.NEGATIVE_INFINITY));
        assertEquals(1f, MixLevels.clamp01(Float.POSITIVE_INFINITY));
        assertEquals(0.25f, MixLevels.clamp01(0.25f));
        assertEquals(0f, MixLevels.clamp01(-0.0f));
        // And through the arithmetic that actually uses it:
        assertEquals(0f, MixLevels.channelVolume(Float.NaN, MixLevels.CURVE_LINEAR,
                MixLevels.XF_THRU, 0.5f, MixLevels.CURVE_LINEAR, 1f));
    }

    @Test
    void theTrimReadoutAndTheAudioUseTheSameLaw() {
        for (double v = 0.05; v <= 1.0; v += 0.05) {
            double fromAudio = 20.0 * Math.log10(ChannelEq.trimGain(v));
            if (v < 0.1) {
                continue; // inside the fade to silence the audio is quieter than the label says
            }
            assertEquals(ChannelEq.trimDb(v), fromAudio, 1e-9, "knob at " + v);
        }
        assertEquals(0.0, ChannelEq.trimDb(0.5), 1e-12);
        assertEquals(ChannelEq.TRIM_BOOST_DB, ChannelEq.trimDb(1.0), 1e-12);
        assertEquals(-ChannelEq.TRIM_CUT_DB, ChannelEq.trimDb(0.0), 1e-12);
        // The old readout claimed -6 dB here while the audio was already 13 dB down.
        assertEquals(-13.0, ChannelEq.trimDb(0.25), 1e-12);
    }
}
