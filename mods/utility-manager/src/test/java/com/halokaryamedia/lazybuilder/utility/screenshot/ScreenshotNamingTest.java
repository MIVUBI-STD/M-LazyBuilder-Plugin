package com.halokaryamedia.lazybuilder.utility.screenshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotNamingTest {
    @Test
    void blankContextFallsBackToMinecraft() {
        assertEquals("minecraft", ScreenshotNaming.sanitize("   "));
        assertEquals("minecraft", ScreenshotNaming.sanitize(null));
    }

    @Test
    void unsafeCharactersBecomeStableFilenameText() {
        assertEquals("my-server-01", ScreenshotNaming.sanitize("  My Server / #01  "));
    }

    @Test
    void repeatedSeparatorsAreCollapsedAndTrimmed() {
        assertEquals("server-name", ScreenshotNaming.sanitize("---Server   Name---"));
    }

    @Test
    void contextIsBoundedToFortyEightCharacters() {
        String sanitized = ScreenshotNaming.sanitize("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        assertEquals(48, sanitized.length());
        assertTrue(sanitized.matches("[a-z0-9._-]+"));
    }
}
