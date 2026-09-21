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
        assertEquals(CapturePreferences.ScreenshotQuality.HIGH, preferences.screenshotQuality());
    }

    @Test
    void qualityRoundTripsAtomically() {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        CapturePreferences expected = new CapturePreferences(
                CapturePreferences.ScreenshotQuality.MAXIMUM
        );

        store.save(expected);

        assertEquals(expected, store.load());
        assertFalse(Files.exists(store.configFile().resolveSibling(
                store.configFile().getFileName() + ".tmp"
        )));
    }

    @Test
    void malformedQualityFallsBackToHigh() throws Exception {
        CaptureConfigStore store = new CaptureConfigStore(tempDir);
        Files.writeString(store.configFile(), "screenshot.quality=definitely-not-valid\n");

        assertEquals(
                CapturePreferences.ScreenshotQuality.HIGH,
                store.load().screenshotQuality()
        );
    }
}
