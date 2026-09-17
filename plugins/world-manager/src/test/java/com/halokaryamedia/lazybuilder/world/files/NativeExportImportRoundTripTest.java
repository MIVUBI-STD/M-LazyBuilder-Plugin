package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeExportImportRoundTripTest {
    @TempDir Path tempDir;

    @Test
    void nativeExportArtifactStagesBackAsTrustedJavaAndSanitizesIdentity() throws Exception {
        Path source = tempDir.resolve("source-world");
        Files.createDirectories(source.resolve("region"));
        writeJava1214LevelDat(source.resolve("level.dat"));
        Files.writeString(source.resolve("uid.dat"), "old-uid");
        Files.writeString(source.resolve("session.lock"), "old-lock");
        Files.writeString(source.resolve(".lazybuilder-transfer.properties"), "format=JAVA_1_21_4\n");
        Files.write(source.resolve("region/r.0.0.mca"), new byte[8192]);

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

        Path workspace = tempDir.resolve("work").resolve("staged");
        Files.createDirectories(workspace.getParent());
        WorldImportArtifactStore.StagedImport staged = importStore.stageArchive(
                inboxArtifact.getFileName().toString(), workspace);

        assertEquals(WorldImportArtifactStore.DetectedEdition.JAVA, staged.edition());
        assertEquals("JAVA_1_21_4", staged.trustedFormat());
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("level.dat")));
        assertTrue(Files.isRegularFile(staged.worldDirectory().resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(staged.worldDirectory().resolve("session.lock")));
        assertFalse(Files.exists(staged.worldDirectory().resolve("uid.dat")));
        assertFalse(Files.exists(staged.worldDirectory().resolve(".lazybuilder-transfer.properties")));
    }

    private static void writeJava1214LevelDat(Path path) throws Exception {
        try (var file = Files.newOutputStream(path);
             var gzip = new GZIPOutputStream(file);
             var out = new DataOutputStream(gzip)) {
            out.writeByte(10); // TAG_Compound
            out.writeUTF("");
            out.writeByte(3); // TAG_Int
            out.writeUTF("DataVersion");
            out.writeInt(JavaLevelDataVersion.JAVA_1_21_4);
            out.writeByte(0); // TAG_End
        }
    }
}
