package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactTempRecoveryTest {
    @TempDir Path tempDir;

    @Test
    void removesOnlyOwnedTemporaryArtifactsAcrossExportAndBackupRoots() throws Exception {
        Path exports = Files.createDirectories(tempDir.resolve("exports"));
        Path backups = Files.createDirectories(tempDir.resolve("backups"));
        Path exportTemp = exports.resolve("export-" + UUID.randomUUID() + ".tmp");
        Path backupTemp = backups.resolve("export-" + UUID.randomUUID() + ".tmp");
        Files.writeString(exportTemp, "partial-export");
        Files.writeString(backupTemp, "partial-backup");

        Path keepFinished = exports.resolve("Build.zip");
        Path keepInvalid = exports.resolve("export-not-a-uuid.tmp");
        Path keepDirectory = backups.resolve("export-" + UUID.randomUUID() + ".tmp");
        Files.writeString(keepFinished, "finished");
        Files.writeString(keepInvalid, "unknown");
        Files.createDirectory(keepDirectory);

        int recovered = ArtifactTempRecovery.recover(exports, backups);

        assertEquals(2, recovered);
        assertFalse(Files.exists(exportTemp));
        assertFalse(Files.exists(backupTemp));
        assertTrue(Files.exists(keepFinished));
        assertTrue(Files.exists(keepInvalid));
        assertTrue(Files.isDirectory(keepDirectory));
    }

    @Test
    void missingRootIsHarmless() throws Exception {
        assertEquals(0, ArtifactTempRecovery.recover(tempDir.resolve("missing")));
    }
}
