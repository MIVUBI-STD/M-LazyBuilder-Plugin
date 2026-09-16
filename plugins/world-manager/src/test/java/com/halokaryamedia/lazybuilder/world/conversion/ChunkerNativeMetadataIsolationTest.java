package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerNativeMetadataIsolationTest {
    @TempDir Path tempDir;

    @Test
    void fullNativeRuntimeFreeExportPreservesCustomDimensionsWithoutChunkerMetadata() throws Exception {
        Path input = tempDir.resolve("input-full");
        Files.createDirectories(input.resolve("dimensions/example/moon/region"));
        Files.writeString(input.resolve("level.dat"), "level");
        Files.writeString(input.resolve("dimensions/example/moon/region/r.0.0.mca"), "custom-dimension");

        ChunkerCliAdapter adapter = adapter();
        ConverterAdapter.ConversionRequest request = new ConverterAdapter.ConversionRequest(
                input,
                tempDir.resolve("output-full"),
                "JAVA_1_21_4",
                null,
                null,
                null,
                true
        );

        assertTrue(adapter.canConvertWithoutRuntime(request));
        adapter.convertWithoutRuntime(request);

        assertTrue(Files.isRegularFile(request.outputDirectory().resolve("level.dat")));
        assertTrue(Files.isRegularFile(
                request.outputDirectory().resolve("dimensions/example/moon/region/r.0.0.mca")));
    }

    @Test
    void selectedAreaNativeExportStillRejectsCustomDimensions() throws Exception {
        Path input = tempDir.resolve("input-area");
        Files.createDirectories(input.resolve("dimensions/example/moon/region"));
        Files.writeString(input.resolve("level.dat"), "level");
        Files.writeString(input.resolve("dimensions/example/moon/region/r.0.0.mca"), "custom-dimension");
        Path pruning = tempDir.resolve("area.json");
        Files.writeString(pruning,
                "{\"configs\":{" +
                        "\"minecraft:overworld\":{\"include\":true,\"regions\":[" +
                        "{\"minChunkX\":0,\"minChunkZ\":0,\"maxChunkX\":0,\"maxChunkZ\":0}]}," +
                        "\"minecraft:the_nether\":{\"include\":false,\"regions\":[]}," +
                        "\"minecraft:the_end\":{\"include\":false,\"regions\":[]}" +
                        "}}");

        ChunkerCliAdapter adapter = adapter();
        ConverterAdapter.ConversionRequest request = new ConverterAdapter.ConversionRequest(
                input,
                tempDir.resolve("output-area"),
                "JAVA_1_21_4",
                pruning,
                null,
                null,
                true
        );

        IOException failure = assertThrows(IOException.class, () -> adapter.convertWithoutRuntime(request));
        assertTrue(failure.getMessage().contains("custom dimensions"));
        assertFalse(Files.exists(request.outputDirectory()));
    }

    @Test
    void metadataOnlySeedDoesNotExposeChunkOrCustomDimensionTreesToChunker() throws Exception {
        Path input = tempDir.resolve("input-metadata");
        Files.createDirectories(input.resolve("region"));
        Files.createDirectories(input.resolve("datapacks/custom/data/example/dimension"));
        Files.createDirectories(input.resolve("dimensions/example/moon/region"));
        Files.writeString(input.resolve("level.dat"), "level");
        Files.writeString(input.resolve("level.dat_old"), "old-level");
        Files.writeString(input.resolve("region/r.0.0.mca"), "region");
        Files.writeString(input.resolve("datapacks/custom/data/example/dimension/moon.json"), "{}");
        Files.writeString(input.resolve("dimensions/example/moon/region/r.0.0.mca"), "custom-dimension");

        Path metadata = tempDir.resolve("metadata-only");
        ChunkerCliAdapter.seedMetadataOnlyInput(input, metadata);

        assertTrue(Files.isRegularFile(metadata.resolve("level.dat")));
        assertTrue(Files.isRegularFile(metadata.resolve("level.dat_old")));
        assertFalse(Files.exists(metadata.resolve("region")));
        assertFalse(Files.exists(metadata.resolve("datapacks")));
        assertFalse(Files.exists(metadata.resolve("dimensions")));
    }

    private static ChunkerCliAdapter adapter() {
        return new ChunkerCliAdapter(
                Path.of("/java/bin/java"),
                2048,
                Duration.ofSeconds(10),
                Duration.ofMinutes(30),
                new OnDemandProcessRunner()
        );
    }
}
