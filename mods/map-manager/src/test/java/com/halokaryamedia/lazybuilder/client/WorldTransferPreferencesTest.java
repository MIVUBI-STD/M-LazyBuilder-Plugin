package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldTransferPreferencesTest {
    @TempDir Path tempDir;

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
