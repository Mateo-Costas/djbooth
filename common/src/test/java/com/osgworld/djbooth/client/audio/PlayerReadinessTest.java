package com.osgworld.djbooth.client.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.osgworld.djbooth.client.audio.PlayerReadiness.Stage;
import org.junit.jupiter.api.Test;

/**
 * The rule that cost a whole afternoon: a WaterMedia player resumed or seeked while it is still
 * opening its media never starts its audio decoder. These pin which statuses count as "still
 * opening", so a tidy-up cannot quietly send the first seek back to the first tick.
 */
class PlayerReadinessTest {

    @Test
    void aPlayerStillOpeningIsLeftAlone() {
        assertEquals(Stage.OPENING, PlayerReadiness.of("WAITING")); // a new clock, before its lifecycle thread runs
        assertEquals(Stage.OPENING, PlayerReadiness.of("LOADING")); // the media is being opened
    }

    @Test
    void anUnknownStatusIsTreatedAsStillOpening() {
        // Touching a player too early is the expensive mistake; waiting one more tick is free.
        assertEquals(Stage.OPENING, PlayerReadiness.of(null));
    }

    @Test
    void everyStateAfterOpeningCanBeDriven() {
        for (String s : new String[] {"PAUSED", "PLAYING", "BUFFERING", "ENDED", "STOPPED"}) {
            assertEquals(Stage.UP, PlayerReadiness.of(s), s);
        }
    }

    @Test
    void aPlayerThatGaveUpIsReportedAsFailed() {
        assertEquals(Stage.FAILED, PlayerReadiness.of("ERROR"));
    }
}
