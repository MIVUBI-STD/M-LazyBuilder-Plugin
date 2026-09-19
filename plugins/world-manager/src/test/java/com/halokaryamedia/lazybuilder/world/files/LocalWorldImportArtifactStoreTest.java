package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorldImportArtifactStoreTest {
    @TempDir Path tempDir;

    @Test
    void inspectsNestedJavaWorldBeforePublishing() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("My Build.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Build/region/r.0.0.mca", "region".getBytes(StandardCharsets.UTF_8));
        }

        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        var inspection = store.inspectArtifact("My Build.zip");

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, inspection.edition());
        assertEquals("1.21.4", inspection.sourceVersion());
        assertEquals("My Build", inspection.suggestedName());
        assertEquals("My Build.zip", inspection.artifactName());
    }

    @Test
    void committedCleanupMarkerSurvivesStoreRecreationWithoutGuessingOrdinaryUploads() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Files.writeString(imports.resolve("Committed.zip"), "committed");
        Files.writeString(imports.resolve("Still Reviewing.zip"), "review");

        LocalWorldImportArtifactStore first = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        first.markCommittedCleanupPending("Committed.zip");

        LocalWorldImportArtifactStore restarted = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        assertEquals(List.of("Committed.zip"), restarted.pendingCommittedCleanupArtifacts());

        restarted.deleteArtifact("Committed.zip");
        restarted.clearCommittedCleanupPending("Committed.zip");

        assertTrue(restarted.pendingCommittedCleanupArtifacts().isEmpty());
        assertFalse(Files.exists(imports.resolve("Committed.zip")));
        assertTrue(Files.exists(imports.resolve("Still Reviewing.zip")),
                "startup cleanup must not infer ordinary inbox artifacts are stale");
    }

    @Test
    void inspectionDoesNotInflateUnrelatedWorldPayload() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("Large Build.zip");
        byte[] largeRegion = new byte[2 * 1024 * 1024];
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "Build/region/r.0.0.mca", largeRegion);
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
        }

        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024);
        var inspection = store.inspectArtifact("Large Build.zip");

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, inspection.edition());
        assertEquals("1.21.4", inspection.sourceVersion());
        assertThrows(IOException.class,
                () -> store.stageArchive("Large Build.zip", tempDir.resolve("workspace")));
    }

    @Test
    void inspectsBedrockUsingDbUnderSameArchiveRoot() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("Bedrock Build.mcworld");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "World/level.dat", "bedrock-level".getBytes(StandardCharsets.UTF_8));
            put(zip, "World/db/CURRENT", "db".getBytes(StandardCharsets.UTF_8));
        }

        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        var inspection = store.inspectArtifact("Bedrock Build.mcworld");

        assertEquals(WorldImportArtifactStore.DetectedEdition.BEDROCK, inspection.edition());
        assertEquals("Unknown", inspection.sourceVersion());
        assertEquals("Bedrock Build", inspection.suggestedName());
    }

    @Test
    void rejectsMultipleWorldRootsAtSameArchiveDepth() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports-multi-root"));
        Path archive = imports.resolve("multi-root.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "One/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Two/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
        }

        LocalWorldImportArtifactStore store =
                new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        IOException inspection = assertThrows(IOException.class,
                () -> store.inspectArtifact("multi-root.zip"));
        assertTrue(inspection.getMessage().contains("multiple world roots"));

        IOException staging = assertThrows(IOException.class,
                () -> store.stageArchive("multi-root.zip", tempDir.resolve("multi-root-workspace")));
        assertTrue(staging.getMessage().contains("multiple world roots"));
    }

    @Test
    void rejectsCaseInsensitiveArchivePathCollisions() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports-case-collision"));
        Path archive = imports.resolve("collision.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Build/region/A.mca", "one".getBytes(StandardCharsets.UTF_8));
            put(zip, "Build/region/a.mca", "two".getBytes(StandardCharsets.UTF_8));
        }

        LocalWorldImportArtifactStore store =
                new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        assertThrows(IOException.class,
                () -> store.stageArchive("collision.zip", tempDir.resolve("case-workspace")));
    }

    @Test
    void rejectsAliasedAndWindowsReservedArchivePaths() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports-unsafe-paths"));
        Path aliasArchive = imports.resolve("alias.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(aliasArchive))) {
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Build/foo/../region/r.0.0.mca", "region".getBytes(StandardCharsets.UTF_8));
        }
        LocalWorldImportArtifactStore store =
                new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        assertThrows(IOException.class,
                () -> store.stageArchive("alias.zip", tempDir.resolve("alias-workspace")));

        Path reservedArchive = imports.resolve("reserved.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(reservedArchive))) {
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Build/CON.txt", "unsafe".getBytes(StandardCharsets.UTF_8));
        }
        assertThrows(IOException.class,
                () -> store.stageArchive("reserved.zip", tempDir.resolve("reserved-workspace")));
    }

    @Test
    void stagesNestedJava1214WorldAndSanitizesIdentity() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("world.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "Build/level.dat", javaLevelDat(JavaLevelDataVersion.JAVA_1_21_4));
            put(zip, "Build/region/r.0.0.mca", "region".getBytes(StandardCharsets.UTF_8));
            put(zip, "Build/uid.dat", "uid".getBytes(StandardCharsets.UTF_8));
            put(zip, "Build/session.lock", "lock".getBytes(StandardCharsets.UTF_8));
            put(zip, "Build/.lazybuilder-transfer.properties", "format=JAVA_1_21_4\n".getBytes(StandardCharsets.UTF_8));
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
    void forgedTransferMarkerCannotBypassJavaVersionNormalization() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        Path archive = imports.resolve("old-world.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "level.dat", javaLevelDat(3700));
            put(zip, ".lazybuilder-transfer.properties", "format=JAVA_1_21_4\n".getBytes(StandardCharsets.UTF_8));
        }

        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        var staged = store.stageArchive("old-world.zip", tempDir.resolve("workspace"));

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, staged.edition());
        assertNull(staged.trustedFormat());
    }

    @Test
    void rejectsZipSlip() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(imports.resolve("bad.zip")))) {
            put(zip, "../escape.txt", "bad".getBytes(StandardCharsets.UTF_8));
        }
        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        assertThrows(IOException.class, () -> store.stageArchive("bad.zip", tempDir.resolve("workspace")));
        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
    }

    @Test
    void detectsBedrockByDbDirectory() throws Exception {
        Path imports = Files.createDirectory(tempDir.resolve("imports"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(imports.resolve("world.mcworld")))) {
            put(zip, "level.dat", "bedrock-level".getBytes(StandardCharsets.UTF_8));
            put(zip, "db/CURRENT", "db".getBytes(StandardCharsets.UTF_8));
        }
        LocalWorldImportArtifactStore store = new LocalWorldImportArtifactStore(imports, 100, 1024 * 1024);
        var staged = store.stageArchive("world.mcworld", tempDir.resolve("workspace"));
        assertEquals(WorldImportArtifactStore.DetectedEdition.BEDROCK, staged.edition());
    }

    private static byte[] javaLevelDat(int dataVersion) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(10); // root compound
            out.writeUTF("");
            out.writeByte(10); // Data compound
            out.writeUTF("Data");
            out.writeByte(3); // DataVersion int
            out.writeUTF("DataVersion");
            out.writeInt(dataVersion);
            out.writeByte(0); // end Data
            out.writeByte(0); // end root
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
    }
}
