package com.osgworld.djbooth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional settings, read from {@code config/djbooth.properties}.
 *
 * <p>Right now this holds one thing: a GetSongBPM API key. With a key the booth can look a track's
 * tempo and musical key up when it loads, which is what QUANTIZE, BEAT SYNC and KEY SYNC need in
 * order to work without anyone tapping a tempo in by hand.
 *
 * <p>The key is deliberately <em>not</em> shipped with the mod. Anything embedded in a published
 * jar is scraped within days and the account behind it gets suspended, so each person brings their
 * own — they're free from getsongbpm.com/api. With no key configured the lookup is simply off and
 * everything falls back to the TAP button.
 */
public final class DJBoothConfig {
    private DJBoothConfig() {}

    private static final String FILE_NAME = "djbooth.properties";
    private static final String KEY_API = "getsongbpm_api_key";

    private static final String TEMPLATE = """
            # DJ Booth settings.
            #
            # Optional: a GetSongBPM API key, which lets the booth look up a track's BPM and
            # musical key automatically instead of you tapping the tempo in.
            #
            # Get one free at https://getsongbpm.com/api and paste it below.
            # Leave it blank to keep the lookup switched off.
            #
            # Note: GetSongBPM's terms require a visible credit wherever their data is used. The
            # booth shows one in its GUI while the lookup is enabled, so please leave it in place.
            #
            %s=
            """.formatted(KEY_API);

    private static volatile String apiKey = "";
    private static volatile boolean loaded;

    /** Read the config, creating a commented template on first run. */
    public static synchronized void load(Path configDir) {
        loaded = true;
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file, TEMPLATE);
                apiKey = "";
                return;
            }
            Properties props = new Properties();
            try (var in = Files.newBufferedReader(file)) {
                props.load(in);
            }
            apiKey = props.getProperty(KEY_API, "").trim();
            if (!apiKey.isEmpty()) {
                DJBooth.LOGGER.info("DJ Booth: track lookup enabled (GetSongBPM)");
            }
        } catch (IOException e) {
            DJBooth.LOGGER.warn("DJ Booth: could not read {}, lookup stays off", file, e);
            apiKey = "";
        }
    }

    /** The configured key, or an empty string when the lookup is switched off. */
    public static String apiKey() {
        return apiKey;
    }

    /** Whether track lookup is available at all. */
    public static boolean lookupEnabled() {
        return loaded && !apiKey.isEmpty();
    }
}
