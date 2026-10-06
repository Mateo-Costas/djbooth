package com.osgworld.djbooth.booth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackUrlTest {

    @Test
    void ordinaryLinksPassThroughTrimmed() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                TrackUrl.sanitize("  https://www.youtube.com/watch?v=dQw4w9WgXcQ \n"));
        assertEquals("http://example.com/a.mp3", TrackUrl.sanitize("http://example.com/a.mp3"));
        assertEquals("HTTPS://Example.com/x", TrackUrl.sanitize("HTTPS://Example.com/x"));
    }

    @Test
    void realWorldLinksThatPlayedBeforeStillPass() {
        // The check must never be stricter than the player: each of these loaded in 1.0.1.
        String[] links = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG",
                "https://youtu.be/dQw4w9WgXcQ?si=abc_DEF-123&t=42",
                "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://soundcloud.com/some-artist/some-track?in=playlist/sets/x",
                "https://cdn.discordapp.com/attachments/1/2/a.mp3?ex=65f0&is=65ce&hm=ab12cd&",
                "https://my_host.example.com/audio/a.mp3",          // underscore: URI.getHost() is null
                "https://example.com/search?q=a|b&x={1}&y=^z",      // characters java.net.URI rejects
                "http://example.com:8080/stream.ogg",
                "http://[::1]:8000/a.mp3",
                "https://user:pw@example.com/a.mp3",
                "https://example.com#t=30",
                "https://192.168.1.20/a.mp3",
        };
        for (String link : links) {
            assertEquals(link, TrackUrl.sanitize(link), link);
        }
    }

    @Test
    void aHostThatIsNotAHostIsRefused() {
        assertNull(TrackUrl.sanitize("http://:80/x"));
        assertNull(TrackUrl.sanitize("https://@/x"));
        assertNull(TrackUrl.sanitize("https://exa$mple.com/x"));
        assertNull(TrackUrl.sanitize("https://[::1/x"));
        assertNull(TrackUrl.sanitize("http://\\\\evil.example\\share"));
    }

    @Test
    void emptyMeansClearTheDeck() {
        assertEquals("", TrackUrl.sanitize(""));
        assertEquals("", TrackUrl.sanitize("   "));
        assertEquals("", TrackUrl.sanitize(null));
    }

    @Test
    void onlyHttpAndHttpsAreAccepted() {
        // A UNC path or file URL makes a Windows client authenticate to an attacker's host.
        assertNull(TrackUrl.sanitize("file:///C:/Users/x/secret.mp3"));
        assertNull(TrackUrl.sanitize("file://evil.example/share/a.mp3"));
        assertNull(TrackUrl.sanitize("\\\\evil.example\\share\\a.mp3"));
        assertNull(TrackUrl.sanitize("//evil.example/a.mp3"));
        assertNull(TrackUrl.sanitize("ftp://example.com/a.mp3"));
        assertNull(TrackUrl.sanitize("javascript:alert(1)"));
        assertNull(TrackUrl.sanitize("never gonna give you up"));
    }

    @Test
    void aSchemeAloneIsNotALink() {
        assertNull(TrackUrl.sanitize("http://"));
        assertNull(TrackUrl.sanitize("https:///no-host"));
    }

    @Test
    void embeddedWhitespaceAndControlCharactersAreRefused() {
        assertNull(TrackUrl.sanitize("https://example.com/a b"));
        assertNull(TrackUrl.sanitize("https://example.com/a\tb"));
        assertNull(TrackUrl.sanitize("https://example.com/a\u0000b"));
        assertNull(TrackUrl.sanitize("https://example.com/a\nb"));
    }

    @Test
    void overlongLinksAreRefusedRatherThanCutShort() {
        // Truncating would store a different address from the one that was sent.
        String url = "https://example.com/" + "a".repeat(TrackUrl.MAX_LENGTH);
        assertNull(TrackUrl.sanitize(url));
        String atLimit = "https://example.com/" + "a".repeat(TrackUrl.MAX_LENGTH - 20);
        assertEquals(atLimit, TrackUrl.sanitize(atLimit));
    }
}
