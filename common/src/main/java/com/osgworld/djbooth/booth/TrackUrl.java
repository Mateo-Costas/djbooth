package com.osgworld.djbooth.booth;

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
        return hasHost(url) ? url : null;
    }

    /**
     * Whether the text after {@code scheme://} starts with a plausible host.
     *
     * <p>Read by hand rather than through {@link java.net.URI}: that class rejects a hostname with
     * an underscore (its host comes back null) and throws on characters such as {@code |} or
     * {@code ^} in a query string, and pasted links have both. A link that played in an earlier
     * version should not stop playing because the check was stricter than the player.
     */
    private static boolean hasHost(String url) {
        String rest = url.substring(url.indexOf("://") + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = rest.substring(0, end);
        // Anything before the last '@' is credentials; the host is what follows.
        String hostPort = authority.substring(authority.lastIndexOf('@') + 1);

        String host;
        boolean bracketed = hostPort.startsWith("[");
        if (bracketed) {
            int close = hostPort.indexOf(']');
            if (close < 0) {
                return false;
            }
            host = hostPort.substring(1, close);
        } else {
            int colon = hostPort.lastIndexOf(':');
            host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
        }
        if (host.isEmpty()) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_'
                    || (bracketed && c == ':');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
