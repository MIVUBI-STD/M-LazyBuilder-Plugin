package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

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
