package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void firstRecoveredTargetIsStableWhenUnused() {
        assertEquals(
                tempDir.resolve("session_recovered.mkv"),
                CaptureRecovery.uniqueTarget(tempDir, "session_recovered", ".mkv")
        );
    }
}
