package com.osgworld.djbooth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config file is the one part of this a player edits by hand, so it has to survive being
 * edited by hand: missing, blank, spaced out, or not there at all.
 */
class DJBoothConfigTest {

    private static final String FILE = "djbooth.properties";

    @Test
    void firstRunWritesACommentedTemplateAndLeavesLookupOff(@TempDir Path dir) throws IOException {
        DJBoothConfig.load(dir);

        Path file = dir.resolve(FILE);
        assertTrue(Files.exists(file), "the template should be written on first run");
        String text = Files.readString(file);
        assertTrue(text.contains("getsongbpm_api_key"), "the key someone has to fill in");
        assertTrue(text.contains("getsongbpm.com/api"), "and where to get one");
        assertTrue(text.contains("#"), "with comments explaining it");

        assertFalse(DJBoothConfig.lookupEnabled(), "no key yet, so no lookup");
        assertEquals("", DJBoothConfig.apiKey());
    }

    @Test
    void aConfiguredKeyEnablesTheLookup(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key=abc123\n");
        DJBoothConfig.load(dir);

        assertTrue(DJBoothConfig.lookupEnabled());
        assertEquals("abc123", DJBoothConfig.apiKey());
    }

    @Test
    void surroundingWhitespaceIsForgiven(@TempDir Path dir) throws IOException {
        // Pasting a key out of a web page brings spaces with it.
        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key =   abc123   \n");
        DJBoothConfig.load(dir);

        assertEquals("abc123", DJBoothConfig.apiKey(), "a pasted key shouldn't need trimming by hand");
        assertTrue(DJBoothConfig.lookupEnabled());
    }

    @Test
    void anEmptyOrAbsentKeyLeavesTheLookupOff(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key=\n");
        DJBoothConfig.load(dir);
        assertFalse(DJBoothConfig.lookupEnabled(), "a blank key is not a key");

        Files.writeString(dir.resolve(FILE), "# nothing at all here\n");
        DJBoothConfig.load(dir);
        assertFalse(DJBoothConfig.lookupEnabled(), "a missing entry is not a key either");
    }

    @Test
    void anUnreadableConfigDoesNotStopTheModLoading(@TempDir Path dir) throws IOException {
        // A directory where the file should be: reading it will fail. The booth must carry on
        // with the lookup off rather than taking the mod down over an optional feature.
        Files.createDirectory(dir.resolve(FILE));
        DJBoothConfig.load(dir);
        assertFalse(DJBoothConfig.lookupEnabled());
        assertEquals("", DJBoothConfig.apiKey());
    }

    @Test
    void reloadingReplacesTheKeyRatherThanKeepingTheOldOne(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key=first\n");
        DJBoothConfig.load(dir);
        assertEquals("first", DJBoothConfig.apiKey());

        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key=second\n");
        DJBoothConfig.load(dir);
        assertEquals("second", DJBoothConfig.apiKey(), "a revoked key must not linger");

        Files.writeString(dir.resolve(FILE), "getsongbpm_api_key=\n");
        DJBoothConfig.load(dir);
        assertFalse(DJBoothConfig.lookupEnabled(), "clearing the key must switch the lookup off");
    }
}
