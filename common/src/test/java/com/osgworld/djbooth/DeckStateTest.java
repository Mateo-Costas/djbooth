package com.osgworld.djbooth;

import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckStateTest {

    @Test
    void stoppedDeckReportsCuePosition() {
        DeckState s = new DeckState();
        s.setPlayState(PlayState.CUE);
        s.setCuePointMs(4000);
        s.setOffsetMs(4000);
        assertEquals(4000, s.positionMsAt(999999));
    }

    @Test
    void playingDeckAdvancesWithTimeAndRate() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 1000);      // starts at offset 0
        assertEquals(0, s.positionMsAt(1000));
        assertEquals(500, s.positionMsAt(1500));   // rate 1.0
        s.setRateAt(2.0, 2000);                    // freeze at pos(2000)=1000, then rate 2.0 from t=2000
        assertEquals(1200, s.positionMsAt(2100));  // 1000 + (2100-2000)*2
    }

    @Test
    void pauseFreezesPosition() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        long p = s.positionMsAt(3000);
        s.press(PlayState.PAUSE, 3000);
        assertEquals(p, s.positionMsAt(9999));
    }

    @Test
    void loopWrapsWithinRegion() {
        DeckState s = new DeckState();
        s.setLoop(1000, 2000, true);
        s.press(PlayState.PLAY, 0);
        // raw pos at t=2500 is 2500; wrapped into [1000,2000): 1000 + (2500-1000)%1000 = 1500
        assertEquals(1500, s.positionMsAt(2500));
    }

    @Test
    void cuePreviewFromParkedThenReturns() {
        DeckState s = new DeckState();
        s.setCuePointMs(5000);
        s.press(PlayState.PAUSE, 0);
        s.cue(1000);                       // parked -> preview plays from cue
        assertEquals(PlayState.PLAY, s.getPlayState());
        assertEquals(5000, s.positionMsAt(1000));
        assertEquals(5200, s.positionMsAt(1200));  // advancing
        s.cue(1200);                       // playing -> jump back to cue, pause
        assertEquals(PlayState.CUE, s.getPlayState());
        assertEquals(5000, s.positionMsAt(9999));
    }

    @Test
    void hotCueSetAndJump() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        s.setHotCue(0, 2000);              // pos at t=2000 is 2000
        assertEquals(2000, s.getHotCue(0));
        s.jumpHotCue(0, 6000);             // jump back onto it, still playing
        assertEquals(2000, s.positionMsAt(6000));
        assertEquals(2100, s.positionMsAt(6100));
    }

    @Test
    void loopInOutReloopAndResize() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        s.jumpTo(1000, 0);
        s.loopIn(0);                       // in = 1000
        s.jumpTo(3000, 0);
        s.loopOut(0);                      // out = 3000, looping on
        assertEquals(true, s.isLoopOn());
        assertEquals(2000, s.getLoopOutMs() - s.getLoopInMs());
        s.resizeLoop(0.5);                 // span 2000 -> 1000
        assertEquals(1000, s.getLoopOutMs() - s.getLoopInMs());
        s.loopExit();
        assertEquals(false, s.isLoopOn());
        s.reloop(0);                       // re-enter, jump to in (1000)
        assertEquals(true, s.isLoopOn());
        assertEquals(1000, s.positionMsAt(0));
    }
}
