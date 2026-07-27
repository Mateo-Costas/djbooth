package com.osgworld.djbooth.mixer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings a deck's DSP runs on: a flat one has to leave the audio alone. */
class ChannelSettingsTest {

    @Test
    void flatSettingsAreNeutralEverywhere() {
        ChannelSettings s = ChannelSettings.flat();
        assertEquals(0.5f, s.eqLow());
        assertEquals(0.5f, s.eqMid());
        assertEquals(0.5f, s.eqHigh());
        assertEquals(0.5f, s.colour(), "the COLOR knob's centre detent");
        assertEquals(0.5f, s.trim(), "unity trim");
        assertEquals(0.5f, s.balance(), "balance centred");
        assertEquals(0f, s.echo());
        assertTrue(!s.isolator() && !s.beatOn(), "no effects switched in");
        assertEquals(BeatFxTypes.BANDS_ALL, s.beatBands());
    }

    @Test
    void everyBeatFxAndColourModeIsNamed() {
        assertEquals(BeatFxTypes.TYPES, BeatFxTypes.NAMES.length);
        assertEquals(ColorFxModes.MODES, ColorFxModes.NAMES.length);
        assertEquals(BeatFxTypes.BEATS.length, BeatFxTypes.BEAT_NAMES.length);
        for (int i = 0; i < BeatFxTypes.TYPES; i++) {
            assertTrue(BeatFxTypes.tipKey(i).startsWith("gui.soundsystem_dj.beatfx."),
                    "every effect needs a tooltip key");
        }
    }

    @Test
    void beatFractionsRunSmallestToLargest() {
        for (int i = 1; i < BeatFxTypes.BEATS.length; i++) {
            assertTrue(BeatFxTypes.BEATS[i] > BeatFxTypes.BEATS[i - 1],
                    "beat fractions should be ordered, as they are on the panel");
        }
        assertEquals(1.0, BeatFxTypes.BEATS[BeatFxTypes.DEFAULT_BEAT], 1e-9,
                "the default fraction should be one beat");
    }
}
