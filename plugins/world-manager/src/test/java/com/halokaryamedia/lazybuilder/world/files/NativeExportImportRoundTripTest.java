package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeExportImportRoundTripTest {
    @TempDir Path tempDir;

    @Test
    void nativeExportArtifactStagesAndPublishesBackAsPaperWorldFamily() throws Exception {
        Path source = tempDir.resolve("source-world");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(source.resolve("DIM-1/region"));
        Files.createDirectories(source.resolve("DIM1/region"));
        writeJava1214LevelDat(source.resolve("level.dat"));
        Files.writeString(source.resolve("uid.dat"), "old-uid");
        Files.writeString(source.resolve("session.lock"), "old-lock");
        Files.writeString(source.resolve(".lazybuilder-transfer.properties"), "format=JAVA_1_21_4\n");
        Files.write(source.resolve("region/r.0.0.mca"), new byte[8192]);
        Files.write(source.resolve("DIM-1/region/r.0.0.mca"), new byte[]{1, 2, 3});
        Files.write(source.resolve("DIM1/region/r.0.0.mca"), new byte[]{4, 5, 6});

        Path exports = tempDir.resolve("exports");
        LocalWorldExportArtifactStore exportStore = new LocalWorldExportArtifactStore(exports);
        Path artifact = exportStore.packageDirectory(source, "round-trip", ExportArtifactType.JAVA_ZIP);
        assertTrue(Files.isRegularFile(artifact));

        Path imports = tempDir.resolve("imports");
        Files.createDirectories(imports);
        Path inboxArtifact = imports.resolve(artifact.getFileName());
        Files.copy(artifact, inboxArtifact);

        LocalWorldImportArtifactStore importStore = new LocalWorldImportArtifactStore(
                imports, 10_000, 1024L * 1024L * 1024L);

        WorldImportArtifactStore.ImportInspection inspection =
                importStore.inspectArtifact(inboxArtifact.getFileName().toString());
        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, inspection.edition());
        assertEquals("1.21.4", inspection.sourceVersion());

        Path workRoot = tempDir.resolve("work");
        Path workspace = workRoot.resolve("staged");
        Files.createDirectories(workspace.getParent());
        WorldImportArtifactStore.StagedImport staged = importStore.stageArchive(
                inboxArtifact.getFileName().toString(), workspace);

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, staged.edition());
        assertEquals("JAVA_1_21_4", staged.trustedFormat());
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("level.dat")));
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("DIM1/region/r.0.0.mca")));
        assertFalse(Files.exists(staged.worldDirectory().resolve("session.lock")));
        assertFalse(Files.exists(staged.worldDirectory().resolve("uid.dat")));
        assertFalse(Files.exists(staged.worldDirectory().resolve(".lazybuilder-transfer.properties")));

        Path worlds = Files.createDirectories(tempDir.resolve("worlds"));
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, workRoot);
        repository.publishStagedWorld(staged.worldDirectory(), "Imported");
        repository.markPublishedWorldCommitted("Imported");

        assertTrue(Files.isRegularFile(worlds.resolve("Imported/level.dat")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported/region/r.0.0.mca")));
        assertFalse(Files.exists(worlds.resolve("Imported/DIM-1")));
        assertFalse(Files.exists(worlds.resolve("Imported/DIM1")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_nether/level.dat")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_nether/DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_the_end/level.dat")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_the_end/DIM1/region/r.0.0.mca")));
        assertFalse(Files.exists(worlds.resolve("Imported/.lazybuilder-publish-pending")));
        assertFalse(Files.exists(worlds.resolve("Imported_nether/.lazybuilder-family-publish-pending")));
        assertFalse(Files.exists(worlds.resolve("Imported_the_end/.lazybuilder-family-publish-pending")));
    }

    @Test
    void worldBackupZipRestoresAsACopyWithoutOverwritingSourceOrBackup() throws Exception {
        Path worlds = Files.createDirectories(tempDir.resolve("worlds"));
        Path original = Files.createDirectories(worlds.resolve("Builder-Original"));
        Files.createDirectories(original.resolve("region"));
        writeJava1214LevelDat(original.resolve("level.dat"));
        byte[] regionBytes = new byte[]{1, 3, 5, 7, 9};
        Files.write(original.resolve("region/r.0.0.mca"), regionBytes);

        Path backupRoot = Files.createDirectories(tempDir.resolve("backups"));
        Path backup = new LocalWorldBackupStore(backupRoot).createBackup(
                original, "Builder-Original-11111111-2222-3333-4444-555555555555");
        byte[] backupBytes = Files.readAllBytes(backup);
        byte[] originalBytes = Files.readAllBytes(original.resolve("level.dat"));

        // This is the current Desktop restore-as-copy route: preserve the ZIP
        // in backups/, upload an inbox copy, stage with its admitted digest,
        // then publish under a new folder through the canonical World repository.
        Path imports = Files.createDirectories(tempDir.resolve("imports"));
        Path upload = Files.copy(backup, imports.resolve(backup.getFileName()));
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(upload)));
        LocalWorldImportArtifactStore importStore = new LocalWorldImportArtifactStore(
                imports, 10_000, 1024L * 1024L * 1024L);

        Path work = Files.createDirectories(tempDir.resolve("work"));
        WorldImportArtifactStore.StagedImport staged = importStore.stageArchive(
                upload.getFileName().toString(), work.resolve("restore-staging"), digest);
        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, staged.edition());
        assertEquals("JAVA_1_21_4", staged.trustedFormat());

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, work);
        repository.publishStagedWorld(staged.worldDirectory(), "Builder-Restored-Copy");
        repository.markPublishedWorldCommitted("Builder-Restored-Copy");

        assertArrayEquals(originalBytes, Files.readAllBytes(original.resolve("level.dat")));
        assertArrayEquals(regionBytes, Files.readAllBytes(original.resolve("region/r.0.0.mca")));
        assertArrayEquals(regionBytes, Files.readAllBytes(worlds.resolve("Builder-Restored-Copy/region/r.0.0.mca")));
        assertArrayEquals(backupBytes, Files.readAllBytes(backup));
        assertTrue(Files.isRegularFile(worlds.resolve("Builder-Original/level.dat")));
        assertTrue(Files.isRegularFile(worlds.resolve("Builder-Restored-Copy/level.dat")));
    }

    private static void writeJava1214LevelDat(Path path) throws Exception {
        try (var file = Files.newOutputStream(path);
             var gzip = new GZIPOutputStream(file);
             var out = new DataOutputStream(gzip)) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(3);
            out.writeUTF("DataVersion");
            out.writeInt(JavaLevelDataVersion.JAVA_1_21_4);
            out.writeByte(0);
        }
    }
}
