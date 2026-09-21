package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureNamingTest {
    @Test
    void blankContextFallsBackToMinecraft() {
        assertEquals("minecraft", CaptureNaming.sanitize("   "));
        assertEquals("minecraft", CaptureNaming.sanitize(null));
    }

    @Test
    void unsafeCharactersBecomeStableFilenameText() {
        assertEquals("my-server-01", CaptureNaming.sanitize("  My Server / #01  "));
    }

    @Test
    void contextIsBoundedToFortyEightCharacters() {
        String sanitized = CaptureNaming.sanitize("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        assertEquals(48, sanitized.length());
        assertTrue(sanitized.matches("[a-z0-9._-]+"));
    }

    @Test
    void qualityPresetsExposeExpectedFormats() {
        assertEquals(".jpg", CapturePreferences.ScreenshotQuality.EFFICIENT.extension());
        assertEquals(".jpg", CapturePreferences.ScreenshotQuality.HIGH.extension());
        assertEquals(".png", CapturePreferences.ScreenshotQuality.MAXIMUM.extension());
        assertTrue(CapturePreferences.ScreenshotQuality.HIGH.jpegQuality()
                > CapturePreferences.ScreenshotQuality.EFFICIENT.jpegQuality());
    }
}
