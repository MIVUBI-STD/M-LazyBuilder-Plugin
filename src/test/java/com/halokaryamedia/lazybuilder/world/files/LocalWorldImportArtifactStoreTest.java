package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorldImportArtifactStoreTest {
    @TempDir Path tempDir;

    @Test
    void stagesNestedNativeExportAndSanitizesIdentity() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("world.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "Build/level.dat", "level");
            put(zip, "Build/region/r.0.0.mca", "region");
            put(zip, "Build/uid.dat", "uid");
            put(zip, "Build/session.lock", "lock");
            put(zip, "Build/.lazybuilder-transfer.properties", "format=JAVA_1_21_4\n");
        }

        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        Path workspace = tempDir.resolve("workspace");
        WorldImportArtifactStore.StagedImport staged = store.stageArchive("world.zip", workspace);

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, staged.edition());
        assertEquals("JAVA_1_21_4", staged.trustedFormat());
        assertTrue(Files.exists(workspace.resolve("level.dat")));
        assertFalse(Files.exists(workspace.resolve("uid.dat")));
        assertFalse(Files.exists(workspace.resolve("session.lock")));
        assertFalse(Files.exists(workspace.resolve(".lazybuilder-transfer.properties")));
    }

    @Test
    void rejectsZipSlip() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(imports.resolve("bad.zip")))) {
            put(zip, "../escape.txt", "bad");
        }
        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        assertThrows(IOException.class, () -> store.stageArchive("bad.zip", tempDir.resolve("workspace")));
        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
    }

    @Test
    void detectsBedrockByDbDirectory() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(imports.resolve("world.mcworld")))) {
            put(zip, "level.dat", "bedrock-level");
            put(zip, "db/CURRENT", "db");
        }
        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        var staged = store.stageArchive("world.mcworld", tempDir.resolve("workspace"));
        assertEquals(WorldImportArtifactStore.DetectedEdition.BEDROCK, staged.edition());
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
