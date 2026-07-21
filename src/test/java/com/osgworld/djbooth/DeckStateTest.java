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
}
