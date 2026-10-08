package com.osgworld.djbooth.client.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osgworld.djbooth.mixer.BeatFxTypes;
import com.osgworld.djbooth.mixer.ColorFxModes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A missing language key shows as the raw key on the panel, and a key shared by two meanings shows
 * the wrong sentence: the window title was a knob's tooltip until the screenshot showed it.
 */
class LangKeysTest {

    private static final Path LANG = Path.of("src/main/resources/assets/soundsystem_dj/lang");
    private static final Path SOURCES = Path.of("src/main/java");

    private static JsonObject lang(String file) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every {@code "gui.soundsystem_dj.something"} written out in full in the sources. */
    private static Set<String> literalKeysInSources() throws IOException {
        Pattern pattern = Pattern.compile("\"(gui\\.soundsystem_dj\\.[a-z_.]*[a-z_])\"");
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Matcher m = pattern.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        return keys;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        Set<String> en = new TreeSet<>(lang("en_us.json").keySet());
        Set<String> es = new TreeSet<>(lang("es_es.json").keySet());
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(es);
        Set<String> onlyEs = new TreeSet<>(es);
        onlyEs.removeAll(en);
        assertTrue(onlyEn.isEmpty(), "missing from es_es.json: " + onlyEn);
        assertTrue(onlyEs.isEmpty(), "missing from en_us.json: " + onlyEs);
    }

    @Test
    void everyKeyWrittenInTheCodeIsTranslated() throws IOException {
        Set<String> have = lang("en_us.json").keySet();
        for (String key : literalKeysInSources()) {
            // "gui.soundsystem_dj.hotcue" and friends are completed at run time; the prefix alone is
            // not a key, and the regex only keeps complete words, so this is the full set.
            if (key.endsWith(".beatfx") || key.endsWith(".color") || key.endsWith(".status")) {
                continue;
            }
            assertTrue(have.contains(key), "no translation for " + key);
        }
    }

    @Test
    void everyEffectAndColourModeHasATooltip() throws IOException {
        Set<String> have = lang("en_us.json").keySet();
        for (int i = 0; i < BeatFxTypes.TYPES; i++) {
            assertTrue(have.contains(BeatFxTypes.tipKey(i)), "BEAT FX tooltip " + BeatFxTypes.tipKey(i));
        }
        for (int i = 0; i < ColorFxModes.MODES; i++) {
            assertTrue(have.contains(ColorFxModes.tipKey(i)), "COLOR FX tooltip " + ColorFxModes.tipKey(i));
        }
    }

    @Test
    void theDeckStatusLinesAreAllThere() throws IOException {
        for (String key : new String[]{"no_track", "loading", "failed", "no_backend"}) {
            for (String file : new String[]{"en_us.json", "es_es.json"}) {
                assertTrue(lang(file).has("gui.soundsystem_dj.status." + key), file + " lacks status." + key);
            }
        }
    }

    @Test
    void theWindowTitleIsNotAKnobTooltip() throws IOException {
        // The title key and the BOOTH MONITOR knob's tooltip key must be different keys with
        // different text: sharing one put a sentence about a knob across the top of the screen.
        for (String file : new String[]{"en_us.json", "es_es.json"}) {
            JsonObject l = lang(file);
            assertTrue(l.has("gui.soundsystem_dj.title"), file + " lacks the title");
            String title = l.get("gui.soundsystem_dj.title").getAsString();
            String knob = l.get("gui.soundsystem_dj.booth").getAsString();
            assertTrue(title.length() < knob.length(), "the title should be a name, not a sentence: " + title);
            assertEquals(false, title.equals(knob));
        }
    }
}
