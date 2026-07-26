package com.osgworld.djbooth;

import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CDJ-3000 deck controls: direction, slip, quantize, memory cues and sync. */
class DeckCdjTest {

    private static DeckState playing(long now) {
        DeckState s = new DeckState();
        s.setTrackUrl("test");
        s.press(PlayState.PLAY, now);
        return s;
    }

    @Test
    void reverseRunsThePositionBackwards() {
        DeckState s = playing(0);
        s.jumpTo(10_000, 0);
        assertEquals(11_000, s.positionMsAt(1000), 2, "forward playback advances");

        s.setDirection(DeckState.DIR_REV);
        s.jumpTo(10_000, 0);
        assertEquals(9_000, s.positionMsAt(1000), 2, "reverse playback rewinds");
        assertTrue(s.isReverse());
    }

    @Test
    void positionNeverGoesNegativeInReverse() {
        DeckState s = playing(0);
        s.setDirection(DeckState.DIR_REV);
        s.jumpTo(500, 0);
        assertEquals(0, s.positionMsAt(60_000), "rewinding past the start clamps at zero");
    }

    @Test
    void aLoopHoldsThePlayheadWhicheverWayTheDeckRuns() {
        DeckState s = playing(0);
        s.setLoop(10_000, 12_000, true);

        // Forwards: running off the out point wraps back to the in point.
        s.jumpTo(11_500, 0);
        assertEquals(11_000, s.positionMsAt(1_500), 2, "forward loop should wrap");

        // Reverse: running back off the in point wraps up to the out point. Without this the
        // playhead simply escapes the loop and keeps rewinding.
        s.setDirection(DeckState.DIR_REV);
        s.jumpTo(10_500, 0);
        long p = s.positionMsAt(1_000); // 1 s back from 10 500 would be 9 500, outside the loop
        assertTrue(p >= 10_000 && p < 12_000,
                "reverse playback should stay inside the loop, was " + p);
        assertEquals(11_500, p, 2);
    }

    @Test
    void quantizeSnapsCuesToTheBeatOnlyWhenItIsOn() {
        DeckState s = playing(0);
        s.setBpm(120); // one beat every 500 ms
        s.setQuantize(true);
        s.jumpTo(1_120, 0);
        s.setCueHere(0);
        assertEquals(1_000, s.getCuePointMs(), "1120 ms snaps to the beat at 1000 ms");

        s.setQuantize(false);
        s.jumpTo(1_120, 0);
        s.setCueHere(0);
        assertEquals(1_120, s.getCuePointMs(), "with QUANTIZE off the cue lands where you put it");
    }

    @Test
    void quantizeIsInertWithoutATempo() {
        DeckState s = playing(0);
        s.setQuantize(true); // but no BPM tapped yet
        assertEquals(1_234, s.quantise(1_234), "nothing to snap to, so nothing moves");
    }

    @Test
    void slipReturnsToWhereTheTrackWouldHaveBeen() {
        DeckState s = playing(0);
        s.jumpTo(10_000, 0);
        s.setSlip(true);
        s.beginSlip(0);
        // Scratch backwards while the shadow timeline keeps running.
        s.jumpTo(2_000, 1_000);
        assertEquals(11_000, s.slipUnderlyingMs(1_000), 2, "the track kept running underneath");
        s.endSlip(1_000);
        assertEquals(11_000, s.positionMsAt(1_000), 2, "letting go lands where it should");
        assertFalse(s.isSlipping());
    }

    @Test
    void memoryCuesSaveRecallAndDelete() {
        DeckState s = playing(0);
        s.jumpTo(5_000, 0);
        s.memorise(0);
        s.jumpTo(20_000, 0);
        s.memorise(0);
        assertEquals(2, s.getMemoryCues().size());

        s.memorise(0); // same spot again
        assertEquals(2, s.getMemoryCues().size(), "the same spot shouldn't be saved twice");

        s.callMemory(1, 0);
        assertTrue(s.getMemoryCues().contains(s.getCuePointMs()), "call lands on a saved cue");

        s.jumpTo(20_100, 0);
        s.deleteMemory(0);
        assertEquals(1, s.getMemoryCues().size(), "the nearest cue is the one that goes");
        assertEquals(5_000, s.getMemoryCues().get(0));
    }

    @Test
    void beatSyncMatchesTheOtherDeckAndRefusesWithoutATempo() {
        DeckState s = playing(0);
        s.setBpm(128);
        assertTrue(s.syncTo(140, 0));
        assertEquals(140.0 / 128.0, s.getRate(), 1e-9);

        DeckState untapped = playing(0);
        assertFalse(untapped.syncTo(140, 0), "nothing to sync from without a tempo");
        assertEquals(1.0, untapped.getRate(), 1e-9, "and the rate is left alone");
    }

    @Test
    void tempoResetReturnsToTheTracksOwnSpeed() {
        DeckState s = playing(0);
        s.setRateAt(1.16, 0);
        s.resetTempo(0);
        assertEquals(1.0, s.getRate(), 1e-9);
    }
}
