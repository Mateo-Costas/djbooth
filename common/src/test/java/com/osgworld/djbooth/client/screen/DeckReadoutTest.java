package com.osgworld.djbooth.client.screen;

import com.osgworld.djbooth.client.screen.widget.PanelMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckReadoutTest {

    @Test
    void aDeckAtItsOwnSpeedPrintsNoTempo() {
        assertEquals("", DeckReadout.tempoText(1.0));
        assertEquals("", DeckReadout.tempoText(1.0002), "a hair off centre is still centre");
    }

    @Test
    void tempoIsPrintedAsASignedPercentage() {
        assertEquals("+4.0%", DeckReadout.tempoText(1.04));
        assertEquals("-6.0%", DeckReadout.tempoText(0.94));
        assertEquals("+16.0%", DeckReadout.tempoText(1.16));
    }

    @Test
    void theDecimalPointDoesNotDependOnTheLanguage() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"));
            assertEquals("+4.5%", DeckReadout.tempoText(1.045));
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void aNonNumericRateIsNotPrinted() {
        assertEquals("", DeckReadout.tempoText(Double.NaN));
        assertEquals("", DeckReadout.tempoText(Double.POSITIVE_INFINITY));
    }

    @Test
    void theJogPlatterTurnsAtThirtyThreeAndAThirdRpm() {
        assertEquals(0.0, PanelMath.platterDegrees(0), 1e-9);
        assertEquals(200.0, PanelMath.platterDegrees(1000), 1e-9, "200 degrees a second");
        assertEquals(0.0, PanelMath.platterDegrees(1800), 1e-9, "one turn takes 1.8 s");
        // 33 1/3 rpm is 1.8 seconds a turn.
        assertEquals(60.0 / (100.0 / 3.0), 1.8, 1e-9);
    }

    @Test
    void thePlatterRunsBackwardsWhenThePositionDoes() {
        double a = PanelMath.platterDegrees(5000);
        double b = PanelMath.platterDegrees(4900);
        assertTrue(b < a, "an earlier point in the track is an earlier angle");
        assertEquals(-20.0, b - a, 1e-9);
    }

    @Test
    void thePlatterAngleStaysBoundedOverALongTrack() {
        for (long ms = 0; ms < 3_600_000; ms += 12_345) {
            double d = PanelMath.platterDegrees(ms);
            assertTrue(d > -360.0 && d < 360.0, "angle " + d + " at " + ms + " ms");
        }
    }
}
