package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorldExportArtifactStoreTest {
    @TempDir Path tempDir;

    @Test
    void packagesFreshArtifactAndRejectsExistingTarget() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("level.dat"), "level");
        Path exports = tempDir.resolve("exports");
        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(exports);

        Path artifact = store.packageDirectory(
                source,
                "Build",
                ExportArtifactType.JAVA_ZIP
        );

        assertTrue(Files.isRegularFile(artifact));
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            assertTrue(zip.getEntry("level.dat") != null);
        }
        assertThrows(java.io.IOException.class, () ->
                store.packageDirectory(
                        source,
                        "Build",
                        ExportArtifactType.JAVA_ZIP
                ));
    }

    @Test
    void finalPublicationRefusesExistingDestinationEvenAfterEarlierPreflight() throws Exception {
        // Model a second export publishing between exists(target) and rename.
        Path exports = Files.createDirectory(tempDir.resolve("race-exports"));
        Path staged = exports.resolve("export-candidate.tmp");
        Path published = exports.resolve("Build.zip");
        Files.writeString(staged, "new candidate");
        Files.writeString(published, "previously committed");

        assertThrows(FileAlreadyExistsException.class,
                () -> LocalWorldExportArtifactStore.moveIntoPlace(staged, published));
        assertEquals("previously committed", Files.readString(published));
        assertEquals("new candidate", Files.readString(staged),
                "caller still owns temporary file for finally cleanup");
    }

    @Test
    void rejectsNonDirectoryExportRoot() throws Exception {
        Path root = tempDir.resolve("exports-as-file");
        Files.writeString(root, "not-a-directory");
        Path source = Files.createDirectory(tempDir.resolve("source-two"));
        Files.writeString(source.resolve("level.dat"), "level");

        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(root);
        assertThrows(java.io.IOException.class, () ->
                store.packageDirectory(
                        source,
                        "Build",
                        ExportArtifactType.JAVA_ZIP
                ));
    }
}
