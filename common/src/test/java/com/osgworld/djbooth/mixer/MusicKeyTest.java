package com.osgworld.djbooth.mixer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Musical keys and the Camelot arithmetic KEY SYNC leans on. */
class MusicKeyTest {

    @Test
    void camelotAnchorsMatchTheWheel() {
        // The two everyone knows: A minor is 8A and its relative major, C, is 8B.
        assertEquals("8A", new MusicKey(9, true).camelot());
        assertEquals("8B", new MusicKey(0, false).camelot());
    }

    @Test
    void everyKeyHasADistinctCamelotSlot() {
        var seen = new java.util.HashSet<String>();
        for (int root = 0; root < 12; root++) {
            for (boolean minor : new boolean[]{true, false}) {
                assertTrue(seen.add(new MusicKey(root, minor).camelot()),
                        "each of the 24 keys needs its own wheel position");
            }
        }
        assertEquals(24, seen.size());
    }

    @Test
    void parsesNoteNamesCamelotAndRoundTrips() {
        assertEquals(new MusicKey(9, true), MusicKey.parse("Am"));
        assertEquals(new MusicKey(0, false), MusicKey.parse("C"));
        assertEquals(new MusicKey(6, true), MusicKey.parse("F#m"));
        assertEquals(new MusicKey(9, true), MusicKey.parse("8A"));
        assertEquals(new MusicKey(0, false), MusicKey.parse("8B"));

        for (int root = 0; root < 12; root++) {
            for (boolean minor : new boolean[]{true, false}) {
                MusicKey k = new MusicKey(root, minor);
                assertEquals(k, MusicKey.parse(k.camelot()), "camelot should round-trip");
                assertEquals(k, MusicKey.parse(k.toString()), "note name should round-trip");
            }
        }
    }

    @Test
    void refusesGarbageRatherThanGuessing() {
        assertNull(MusicKey.parse(null));
        assertNull(MusicKey.parse(""));
        assertNull(MusicKey.parse("   "));
        assertNull(MusicKey.parse("13A"), "there is no 13 on the wheel");
        assertNull(MusicKey.parse("banana"));
    }

    @Test
    void semitoneDistanceAlwaysTakesTheShortWayRound() {
        // C up to D is two semitones; C down to A# is two the other way, not ten.
        assertEquals(2, new MusicKey(0, false).semitonesTo(new MusicKey(2, false)));
        assertEquals(-2, new MusicKey(0, false).semitonesTo(new MusicKey(10, false)));
        assertEquals(0, new MusicKey(5, true).semitonesTo(new MusicKey(5, true)));

        for (int a = 0; a < 12; a++) {
            for (int b = 0; b < 12; b++) {
                int d = new MusicKey(a, false).semitonesTo(new MusicKey(b, false));
                assertTrue(d >= -6 && d <= 6, "never shift a track further than half an octave");
            }
        }
    }

    @Test
    void compatibilityFollowsTheCamelotRule() {
        MusicKey aMinor = new MusicKey(9, true);      // 8A
        assertTrue(aMinor.mixesWith(aMinor), "a key mixes with itself");
        assertTrue(aMinor.mixesWith(new MusicKey(0, false)), "8A and its relative major 8B");
        assertTrue(aMinor.mixesWith(new MusicKey(4, true)), "8A and its neighbour 9A");
        assertTrue(aMinor.mixesWith(new MusicKey(2, true)), "8A and its neighbour 7A");
        assertFalse(aMinor.mixesWith(new MusicKey(3, true)), "8A and 2A are across the wheel");
    }

    @Test
    void keySyncLandsInTheTargetKey() {
        MusicKey from = new MusicKey(9, true);  // Am
        MusicKey to = new MusicKey(11, true);   // Bm
        int shift = from.semitonesTo(to);
        assertEquals(2, shift);
        assertEquals(to, new MusicKey(from.root() + shift, from.minor()),
                "shifting by that many semitones should land exactly on the target");
    }
}
