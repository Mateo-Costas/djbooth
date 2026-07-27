package com.osgworld.djbooth.client.audio;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.DJBoothConfig;
import com.osgworld.djbooth.mixer.TrackInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks a track's tempo and musical key up by name, so QUANTIZE, BEAT SYNC and KEY SYNC have
 * something to work with without anyone tapping a tempo in.
 *
 * <p>Backed by <a href="https://getsongbpm.com">GetSongBPM</a>, which needs an API key the player
 * supplies themselves (see {@link DJBoothConfig}). With no key configured every call here is a
 * no-op and the booth carries on exactly as it did before.
 *
 * <p>Lookups run off the client thread and are cached for the session, because the same handful of
 * tracks get reloaded constantly while mixing and the free tier is rate-limited. A miss is cached
 * too: a track the database doesn't have shouldn't be asked about again every time it's cued.
 */
public final class TrackLookup {
    private TrackLookup() {}

    private static final TrackInfo NOTHING = TrackInfo.NOTHING;
    private static final String ENDPOINT = "https://api.getsong.co/search/";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private static final Map<String, TrackInfo> CACHE = new ConcurrentHashMap<>();
    private static volatile HttpClient client;

    /**
     * Look {@code query} up and hand the result back on the client thread.
     *
     * <p>{@code query} is whatever the player typed into the deck, so it may be a bare song title,
     * "artist - title", or a URL. URLs are skipped: there's no name in them to search on.
     */
    public static void lookup(String query, java.util.function.Consumer<TrackInfo> onResult) {
        if (!DJBoothConfig.lookupEnabled() || query == null) {
            return;
        }
        String cleaned = clean(query);
        if (cleaned.isEmpty()) {
            return;
        }
        TrackInfo cached = CACHE.get(cleaned);
        if (cached != null) {
            if (!cached.isEmpty()) {
                onResult.accept(cached);
            }
            return;
        }
        Thread t = new Thread(() -> {
            TrackInfo info = fetch(cleaned);
            CACHE.put(cleaned, info);
            if (!info.isEmpty()) {
                net.minecraft.client.Minecraft.getInstance().execute(() -> onResult.accept(info));
            }
        }, "djbooth-track-lookup");
        t.setDaemon(true);
        t.start();
    }

    /** Strip the noise typed searches pick up, so the same track isn't looked up five ways. */
    private static String clean(String query) {
        String s = query.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return ""; // nothing searchable in a URL
        }
        // Drop the "(Official Video)" / "[HD]" tails that come with copy-pasted titles.
        s = s.replaceAll("(?i)[\\[(][^\\])]*(official|video|audio|lyric|hd|4k|remaster)[^\\])]*[\\])]", "");
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static TrackInfo fetch(String query) {
        try {
            String url = ENDPOINT
                    + "?api_key=" + enc(DJBoothConfig.apiKey())
                    + "&type=song&limit=1&lookup=" + enc(query);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "DJBooth/" + DJBooth.MODID)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                DJBooth.LOGGER.warn("Track lookup for '{}' returned HTTP {}",
                        query, response.statusCode());
                return NOTHING;
            }
            return TrackInfo.parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NOTHING;
        } catch (Exception e) {
            DJBooth.LOGGER.warn("Track lookup for '{}' failed", query, e);
            return NOTHING;
        }
    }

    /** Percent-encode a query component; song titles are full of spaces and punctuation. */
    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static HttpClient http() {
        HttpClient c = client;
        if (c == null) {
            synchronized (TrackLookup.class) {
                c = client;
                if (c == null) {
                    c = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
                    client = c;
                }
            }
        }
        return c;
    }

}
