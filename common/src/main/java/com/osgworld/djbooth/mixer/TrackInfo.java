package com.osgworld.djbooth.mixer;

import java.util.Locale;

/**
 * What a metadata lookup managed to find out about a track: its tempo, its musical key, or neither.
 *
 * <p>Both fields are optional on purpose. A database that knows the BPM but not the key is still
 * worth having — that alone makes QUANTIZE and BEAT SYNC work — so a missing key must never cost
 * us the tempo, or the other way round.
 */
public record TrackInfo(double bpm, MusicKey key) {
    public static final TrackInfo NOTHING = new TrackInfo(0, null);

    public boolean isEmpty() {
        return bpm <= 0 && key == null;
    }

    /**
     * Pull the tempo and key out of a lookup response.
     *
     * <p>Deliberately scraping fields rather than binding a schema. The API answers with a
     * different shape depending on which endpoint matched and how, wraps numbers in quotes
     * sometimes and not others, and adds fields over time — so the parse takes what it recognises
     * and leaves the rest unset instead of failing the whole lookup over an unexpected envelope.
     */
    public static TrackInfo parse(String body) {
        if (body == null || body.isBlank()) {
            return NOTHING;
        }
        double bpm = number(body, "tempo");
        MusicKey key = MusicKey.parse(text(body, "key_of"));
        if (key == null) {
            key = MusicKey.parse(text(body, "camelot"));
        }
        return new TrackInfo(bpm > 0 ? bpm : 0, key);
    }

    /** The first value for {@code field}, whether it was quoted or bare. */
    private static String rawValue(String body, String field) {
        String needle = "\"" + field + "\"";
        int at = body.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int colon = body.indexOf(':', at + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
            i++;
        }
        if (i >= body.length()) {
            return null;
        }
        if (body.charAt(i) == '"') {
            int close = body.indexOf('"', i + 1);
            return close < 0 ? null : body.substring(i + 1, close);
        }
        int end = i;
        while (end < body.length() && "-+.0123456789eE".indexOf(body.charAt(end)) >= 0) {
            end++;
        }
        return end == i ? null : body.substring(i, end);
    }

    private static String text(String body, String field) {
        String v = rawValue(body, field);
        return v == null || v.isBlank() || "null".equals(v.toLowerCase(Locale.ROOT)) ? null : v;
    }

    private static double number(String body, String field) {
        String v = text(body, field);
        if (v == null) {
            return 0;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
