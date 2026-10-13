package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTransferPreferencesTest {
    @TempDir Path tempDir;

    @Test
    void unsafeTransferTargetDoesNotReadOrReplaceAnUnrelatedFile() throws Exception {
        Path path = tempDir.resolve("transfer-preferences.properties");
        Path unrelated = tempDir.resolve("important.properties");
        Files.writeString(unrelated, "preserved content");
        try {
            Files.createSymbolicLink(path, unrelated);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable");
        }

        WorldTransferPreferences preferences = new WorldTransferPreferences(path, () -> "server-a");
        preferences.setExportFormat("BEDROCK_1_21_80");
        assertTrue(Files.isSymbolicLink(path));
        assertEquals("preserved content", Files.readString(unrelated));
        assertEquals(WorldTransferPreferences.DEFAULT_EXPORT_FORMAT,
                new WorldTransferPreferences(path, () -> "server-a").exportFormat());
    }

    @Test
    void predictableTransferTempSymlinkCannotOverwriteAnotherFile() throws Exception {
        Path path = tempDir.resolve("transfer-preferences.properties");
        Path unrelated = tempDir.resolve("important.properties");
        Files.writeString(unrelated, "preserved content");
        Path predictable = tempDir.resolve("transfer-preferences.properties.tmp");
        try {
            Files.createSymbolicLink(predictable, unrelated);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable");
        }

        new WorldTransferPreferences(path, () -> "server-a").setExportFormat("BEDROCK_1_21_80");
        assertEquals("preserved content", Files.readString(unrelated));
        assertTrue(Files.isSymbolicLink(predictable));
        assertEquals("BEDROCK_1_21_80",
                new WorldTransferPreferences(path, () -> "server-a").exportFormat());
    }

    @Test
    void unchangedExportFormatDoesNotRewritePreferenceFile() throws Exception {
        Path path = tempDir.resolve("transfer-preferences.properties");
        WorldTransferPreferences preferences = new WorldTransferPreferences(
                path,
                () -> ClientServerIdentity.encode("preferences-test.example:25565")
        );

        preferences.setExportFormat("JAVA_1_21_4");
        FileTime sentinel = FileTime.fromMillis(1_000L);
        Files.setLastModifiedTime(path, sentinel);

        preferences.setExportFormat("JAVA_1_21_4");
        assertEquals(sentinel, Files.getLastModifiedTime(path));

        preferences.setExportFormat("BEDROCK_1_21_80");
        assertNotEquals(sentinel, Files.getLastModifiedTime(path));
        assertEquals("BEDROCK_1_21_80", preferences.exportFormat());
    }
}
