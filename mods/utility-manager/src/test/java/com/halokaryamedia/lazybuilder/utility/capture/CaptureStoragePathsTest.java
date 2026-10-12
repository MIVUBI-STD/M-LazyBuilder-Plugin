package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureStoragePathsTest {
    @TempDir Path gameDir;

    @Test
    void acceptsOwnedVideosDirectoryAndDoesNotCreateFoldersOnRecoveryScan() throws Exception {
        assertNull(CaptureStoragePaths.existingVideoDirectory(gameDir));
        Path owned = CaptureStoragePaths.createVideoDirectory(gameDir);
        assertEquals(gameDir.resolve("captures/videos"), owned);
        assertTrue(Files.isDirectory(owned));
        assertEquals(owned, CaptureStoragePaths.existingVideoDirectory(gameDir));
    }

    @Test
    void refusesCapturesSymlinkToUnrelatedFiles() throws Exception {
        Path other = Files.createDirectories(gameDir.resolve("unrelated-user-folder"));
        try {
            Files.createSymbolicLink(gameDir.resolve("captures"), other);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Filesystem does not permit symbolic links");
        }
        assertThrows(IOException.class, () ->
                CaptureStoragePaths.createVideoDirectory(gameDir));
        assertThrows(IOException.class, () ->
                CaptureStoragePaths.existingVideoDirectory(gameDir));
        assertTrue(Files.isDirectory(other));
    }

    @Test
    void refusesVideosSymlinkToUnrelatedFiles() throws Exception {
        Path captures = Files.createDirectories(gameDir.resolve("captures"));
        Path other = Files.createDirectories(gameDir.resolve("private-videos"));
        try {
            Files.createSymbolicLink(captures.resolve("videos"), other);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Filesystem does not permit symbolic links");
        }
        assertThrows(IOException.class, () ->
                CaptureStoragePaths.createVideoDirectory(gameDir));
        assertThrows(IOException.class, () ->
                CaptureStoragePaths.existingVideoDirectory(gameDir));
        assertTrue(Files.isDirectory(other));
    }
}
