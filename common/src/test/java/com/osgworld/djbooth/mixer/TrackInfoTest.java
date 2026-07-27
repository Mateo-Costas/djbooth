package com.osgworld.djbooth.mixer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing a lookup response.
 *
 * <p>This reads data from someone else's server, so the tests are mostly about what happens when
 * the shape isn't what we hoped: the booth has to shrug and carry on, never throw and never invent
 * a tempo.
 */
class TrackInfoTest {

    @Test
    void readsTempoAndKeyFromATypicalResponse() {
        String body = """
                {"search":[{"id":"abc","title":"Some Track","uri":"https://example",
                "artist":{"name":"Someone"},"tempo":"128","key_of":"8A","time_sig":"4/4"}]}
                """;
        TrackInfo info = TrackInfo.parse(body);
        assertEquals(128.0, info.bpm(), 1e-9);
        assertEquals(new MusicKey(9, true), info.key(), "8A is A minor");
    }

    @Test
    void acceptsBareNumbersAsWellAsQuotedOnes() {
        // The API quotes numbers in some shapes and not others.
        assertEquals(174.0, TrackInfo.parse("{\"tempo\":174,\"key_of\":\"Fm\"}").bpm(), 1e-9);
        assertEquals(174.5, TrackInfo.parse("{\"tempo\":\"174.5\"}").bpm(), 1e-9);
    }

    @Test
    void readsKeysWrittenAsNoteNamesOrCamelot() {
        assertEquals(new MusicKey(5, true), TrackInfo.parse("{\"key_of\":\"Fm\"}").key());
        assertEquals(new MusicKey(0, false), TrackInfo.parse("{\"camelot\":\"8B\"}").key());
    }

    @Test
    void aMissingKeyDoesNotCostUsTheTempo() {
        // The whole point of the two fields being independent: a tempo alone still makes
        // QUANTIZE and BEAT SYNC work.
        TrackInfo info = TrackInfo.parse("{\"tempo\":\"120\"}");
        assertEquals(120.0, info.bpm(), 1e-9);
        assertNull(info.key());
        assertTrue(!info.isEmpty(), "a tempo on its own is still worth having");
    }

    @Test
    void aMissingTempoDoesNotCostUsTheKey() {
        TrackInfo info = TrackInfo.parse("{\"key_of\":\"Am\"}");
        assertEquals(0.0, info.bpm(), 1e-9);
        assertEquals(new MusicKey(9, true), info.key());
        assertTrue(!info.isEmpty());
    }

    @Test
    void nullsAndBlanksAreTreatedAsAbsent() {
        assertTrue(TrackInfo.parse("{\"tempo\":null,\"key_of\":null}").isEmpty());
        assertTrue(TrackInfo.parse("{\"tempo\":\"\",\"key_of\":\"\"}").isEmpty());
        assertTrue(TrackInfo.parse("{\"tempo\":\"unknown\"}").isEmpty(),
                "a tempo we can't read is no tempo, not zero-and-pretend");
    }

    @Test
    void garbageAndEmptyResponsesComeBackEmptyRatherThanThrowing() {
        assertTrue(TrackInfo.parse(null).isEmpty());
        assertTrue(TrackInfo.parse("").isEmpty());
        assertTrue(TrackInfo.parse("   ").isEmpty());
        assertTrue(TrackInfo.parse("<html>rate limited</html>").isEmpty(),
                "an HTML error page is a perfectly likely response");
        assertTrue(TrackInfo.parse("{\"error\":\"api key invalid\"}").isEmpty());
        assertTrue(TrackInfo.parse("{\"search\":[]}").isEmpty());
        assertTrue(TrackInfo.parse("{\"tempo\":").isEmpty(), "truncated response");
    }

    @Test
    void aFieldNamedLikeOursButNotOursIsNotMistakenForIt() {
        // "tempo_confidence" contains "tempo", and a naive search would read its value.
        TrackInfo info = TrackInfo.parse("{\"tempo_confidence\":\"0.9\",\"tempo\":\"128\"}");
        assertEquals(128.0, info.bpm(), 1e-9,
                "should read the tempo field, not the one that merely starts the same");
    }

    @Test
    void negativeOrZeroTemposAreRejected() {
        assertEquals(0.0, TrackInfo.parse("{\"tempo\":\"0\"}").bpm(), 1e-9);
        assertEquals(0.0, TrackInfo.parse("{\"tempo\":\"-120\"}").bpm(), 1e-9);
    }
}
