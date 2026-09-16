package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerConversionDiagnosticsTest {
    @TempDir Path tempDir;

    @Test
    void missingMappingFailsEvenWhenCliReportedCompletion() {
        IOException failure = assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateConversionDiagnostics(
                        "Converting from JAVA 1.21.4 to BEDROCK 1.21.80\n"
                                + "Missing entity mapping for example:custom_mob\n"
                                + "100.00%\nConversion complete! Took 0h 0m 1s 0ms\n"));
        assertTrue(failure.getMessage().contains("unsupported data mapping"));
    }

    @Test
    void nonFatalConversionExceptionFailsEvenWhenCliReportedCompletion() {
        IOException failure = assertThrows(IOException.class, () ->
                ChunkerCliAdapter.validateConversionDiagnostics(
                        "java.lang.Exception: Failed to process BlockEntity {id:example:machine}\n"
                                + "\tat com.hivemc.chunker.SomeWriter.write(SomeWriter.java:1)\n"
                                + "Conversion complete! Took 0h 0m 1s 0ms\n"));
        assertTrue(failure.getMessage().contains("non-fatal data error"));
    }

    @Test
    void cleanCliCompletionPassesDiagnosticGate() {
        assertDoesNotThrow(() -> ChunkerCliAdapter.validateConversionDiagnostics(
                "Converting from JAVA 1.21.4 to BEDROCK 1.21.80\n50.00%\n100.00%\n"
                        + "Conversion complete! Took 0h 0m 1s 0ms\n"));
    }

    @Test
    void existingCustomDimensionDataIsDetectedWithoutDatapackDefinition() throws Exception {
        Path world = tempDir.resolve("world");
        Path customRegion = world.resolve("dimensions/example/moon/region/r.0.0.mca");
        Files.createDirectories(customRegion.getParent());
        Files.writeString(customRegion, "region");

        assertTrue(ChunkerCliAdapter.containsCustomDimensionDefinitions(world));
    }
}
