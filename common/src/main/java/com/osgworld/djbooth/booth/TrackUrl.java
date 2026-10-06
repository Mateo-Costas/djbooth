package com.osgworld.djbooth.booth;

import java.net.URI;
import java.util.Locale;

/**
 * What the server accepts as a track address.
 *
 * <p>The link a player loads onto a deck is not fetched by the server: it is stored and synced, and
 * then every client in range opens it. That makes the link an instruction to other people's
 * computers, so it is held to a narrow shape: {@code http} or {@code https}, with a host, and no
 * whitespace or control characters.
 *
 * <p>The scheme check is the one that matters. Without it a player could load a {@code file:} or a
 * UNC path, and on Windows opening {@code \\host\share} makes the machine try to authenticate to
 * {@code host}, handing over a password hash to whoever runs it.
 */
public final class TrackUrl {
    private TrackUrl() {}

    public static final int MAX_LENGTH = 1024;

    /**
     * The address to store, or {@code null} if it is not acceptable. An empty string is valid and
     * means "clear the deck".
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (url.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 0x7F || Character.isWhitespace(c)) {
                return null;
            }
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
        } catch (java.net.URISyntaxException e) {
            return null;
        }
        return url;
    }
}
