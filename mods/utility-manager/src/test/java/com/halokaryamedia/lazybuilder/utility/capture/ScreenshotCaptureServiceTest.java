package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenshotCaptureServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void newScreenshotDoesNotReplaceExistingFileAfterNameWasSelected() throws Exception {
        Path existing = temporaryDirectory.resolve("image.png");
        Files.writeString(existing, "saved-user-image");
        Path prepared = Files.createTempFile(temporaryDirectory, ".lazybuilder-screenshot-", ".part");
        Files.writeString(prepared, "new-image");

        assertThrows(IOException.class, () ->
                ScreenshotCaptureService.moveIntoPlace(prepared, existing));
        assertEquals("saved-user-image", Files.readString(existing));
        assertEquals("new-image", Files.readString(prepared));
    }

    @Test
    void newScreenshotIsPublishedWhenTargetIsAvailable() throws Exception {
        Path source = Files.createTempFile(temporaryDirectory, ".lazybuilder-screenshot-", ".part");
        Path destination = temporaryDirectory.resolve("capture.jpg");
        Files.writeString(source, "recorded-pixels");

        ScreenshotCaptureService.moveIntoPlace(source, destination);

        assertEquals("recorded-pixels", Files.readString(destination));
        assertTrue(Files.notExists(source));
    }
}
