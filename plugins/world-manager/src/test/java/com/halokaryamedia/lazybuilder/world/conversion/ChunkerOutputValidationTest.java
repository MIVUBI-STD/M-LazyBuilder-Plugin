package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkerOutputValidationTest {
    @TempDir Path tempDir;

    @Test
    void javaOutputRequiresNonEmptyLevelDat() throws Exception {
        Path output = Files.createDirectory(tempDir.resolve("java"));
        assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateOutputDirectory(output, "JAVA_1_21_4"));

        Files.writeString(output.resolve("level.dat"), "nbt");
        assertDoesNotThrow(() ->
                ChunkerCliAdapter.validateOutputDirectory(output, "JAVA_1_21_4"));
    }

    @Test
    void bedrockOutputRequiresLevelDatAndDatabaseData() throws Exception {
        Path output = Files.createDirectory(tempDir.resolve("bedrock"));
        Files.writeString(output.resolve("level.dat"), "nbt");
        assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80"));

        Path database = Files.createDirectory(output.resolve("db"));
        assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80"));

        Files.write(database.resolve("000001.ldb"), new byte[]{1});
        assertDoesNotThrow(() ->
                ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80"));
    }

    @Test
    void emptyLevelDatIsRejectedEvenWhenOutputDirectoryExists() throws Exception {
        Path output = Files.createDirectory(tempDir.resolve("empty-level"));
        Files.createFile(output.resolve("level.dat"));

        assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateOutputDirectory(output, "JAVA_1_20_6"));
    }
}
