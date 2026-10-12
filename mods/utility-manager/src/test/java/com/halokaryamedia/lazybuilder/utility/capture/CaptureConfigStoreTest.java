package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureConfigStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void missingConfigCreatesSimpleDefaults() {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);

        CapturePreferences preferences = store.load();

        assertEquals(CapturePreferences.defaults(), preferences);
        assertTrue(Files.isRegularFile(store.configFile()));
        assertEquals(CapturePreferences.ScreenshotQuality.MAXIMUM, preferences.screenshotQuality());
    }

    @Test
    void qualityRoundTripsAtomically() {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        CapturePreferences expected = new CapturePreferences(
                CapturePreferences.ScreenshotQuality.MAXIMUM,
                CapturePreferences.VideoQuality.PRODUCTION,
                CapturePreferences.VideoResolution.QHD_1440,
                CapturePreferences.VideoFrameRate.FPS_120,
                CapturePreferences.VideoEncoderMode.SOFTWARE
        );

        store.save(expected);

        assertEquals(expected, store.load());
        assertFalse(Files.exists(store.configFile().resolveSibling(
                store.configFile().getFileName() + ".tmp"
        )));
    }

    @Test
    void predictableTemporaryFileIsNeverOverwrittenBySavingPreferences() throws Exception {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        Path oldTemp = store.configFile().resolveSibling(
                store.configFile().getFileName() + ".tmp");
        Files.writeString(oldTemp, "someone-else-owned-this");
        store.save(CapturePreferences.defaults());
        assertEquals("someone-else-owned-this", Files.readString(oldTemp));
        assertEquals(CapturePreferences.defaults(), store.load());
    }

    @Test
    void unsafeTargetDirectoryIsPreserved() throws Exception {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        Files.createDirectory(store.configFile());
        Files.writeString(store.configFile().resolve("notes.txt"), "keep-this");
        store.save(CapturePreferences.defaults());
        assertEquals("keep-this", Files.readString(store.configFile().resolve("notes.txt")));
        assertEquals(CapturePreferences.defaults(), store.load());
    }

    @Test
    void symbolicLinkCaptureConfigDoesNotReadOrModifyTarget() throws Exception {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        Path privateTarget = tempDir.resolve("private.txt");
        Files.writeString(privateTarget, "private");
        try {
            Files.createSymbolicLink(store.configFile(), privateTarget);
        } catch (UnsupportedOperationException | java.io.IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links not supported in this filesystem");
        }
        assertEquals(CapturePreferences.defaults(), store.load());
        store.save(CapturePreferences.defaults());
        assertEquals("private", Files.readString(privateTarget));
        assertTrue(Files.isSymbolicLink(store.configFile()));
    }

    @Test
    void malformedQualityFallsBackToNativePng() throws Exception {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        Files.writeString(store.configFile(), "screenshot.quality=definitely-not-valid\n");

        assertEquals(
                CapturePreferences.ScreenshotQuality.HIGH,
                store.load().screenshotQuality()
        );
    }
}
