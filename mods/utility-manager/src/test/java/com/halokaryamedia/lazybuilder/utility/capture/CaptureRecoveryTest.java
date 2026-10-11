package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;

final class CaptureRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    void recoveredTargetsNeverOverwriteExistingFiles() throws Exception {
        Path first = tempDir.resolve("session_recovered.mp4");
        Files.writeString(first, "existing");

        Path next = CaptureRecovery.uniqueTarget(tempDir, "session_recovered", ".mp4");

        assertEquals(tempDir.resolve("session_recovered_2.mp4"), next);
    }

    @Test
    void recoveredMkvPublicationDoesNotReplaceDestinationCreatedAfterSelection() throws Exception {
        Path source = tempDir.resolve("movie.mkv.partial");
        Files.writeString(source, "original-frames");

        Path chosen = CaptureRecovery.uniqueTarget(tempDir, "movie_recovered", ".mkv");
        Files.writeString(chosen, "unrelated-video");

        assertThrows(IOException.class, () -> CaptureRecovery.preservePartial(source, chosen));
        assertEquals("unrelated-video", Files.readString(chosen));
        assertEquals("original-frames", Files.readString(source));
    }

    @Test
    void recoveredMkvPublicationMovesOnlyItsOwnedPartialFile() throws Exception {
        Path source = tempDir.resolve("movie.mkv.partial");
        Files.writeString(source, "original-frames");
        Path target = tempDir.resolve("movie_recovered.mkv");

        assertEquals(target, CaptureRecovery.preservePartial(source, target));
        assertEquals("original-frames", Files.readString(target));
        assertTrue(Files.notExists(source));
    }

    @Test
    void firstRecoveredTargetIsStableWhenUnused() {
        assertEquals(
                tempDir.resolve("session_recovered.mkv"),
                CaptureRecovery.uniqueTarget(tempDir, "session_recovered", ".mkv")
        );
    }
}
