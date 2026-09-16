package com.halokaryamedia.lazybuilder.world.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldAreaSelectionTest {
    @TempDir Path tempDir;

    @Test
    void cornersNormalizeAndExpandToWholeChunksWithNegativeCoordinates() {
        WorldAreaSelection area = WorldAreaSelection.ofCorners(
                "minecraft:overworld", 31, 48, -17, -1);

        assertEquals("minecraft:overworld", area.dimensionId());
        assertEquals(-32, area.minBlockX());
        assertEquals(-16, area.minBlockZ());
        assertEquals(31, area.maxBlockX());
        assertEquals(63, area.maxBlockZ());
        assertEquals(-2, area.minChunkX());
        assertEquals(-1, area.minChunkZ());
        assertEquals(1, area.maxChunkX());
        assertEquals(3, area.maxChunkZ());
        assertEquals(4, area.chunkWidth());
        assertEquals(5, area.chunkDepth());
        assertEquals(64, area.blockWidth());
        assertEquals(80, area.blockDepth());
        assertEquals(20L, area.chunkCount());
    }

    @Test
    void alreadyChunkAlignedSelectionRemainsStable() {
        WorldAreaSelection area = WorldAreaSelection.ofCorners(
                "minecraft:the_nether", 112, -352, 847, 239);

        assertEquals(112, area.minBlockX());
        assertEquals(-352, area.minBlockZ());
        assertEquals(847, area.maxBlockX());
        assertEquals(239, area.maxBlockZ());
        assertEquals(7, area.minChunkX());
        assertEquals(-22, area.minChunkZ());
        assertEquals(52, area.maxChunkX());
        assertEquals(14, area.maxChunkZ());
        assertEquals(46, area.chunkWidth());
        assertEquals(37, area.chunkDepth());
    }

    @Test
    void pruningDocumentKeepsRectangleOnlyInSelectedDimension() throws Exception {
        WorldAreaSelection area = WorldAreaSelection.ofCorners(
                "minecraft:the_nether", -17, -1, 31, 48);
        Path pruning = WorldExportService.writeAreaPruning(area, tempDir);
        String json = Files.readString(pruning);
        String fullExclusion = "{\"minChunkX\":-2147483648,\"minChunkZ\":-2147483648,"
                + "\"maxChunkX\":2147483647,\"maxChunkZ\":2147483647}";

        assertTrue(json.contains("\"minecraft:overworld\":{\"include\":false,\"regions\":[" + fullExclusion + "]}"));
        assertTrue(json.contains("\"minecraft:the_nether\":{\"include\":true,\"regions\":[{\"minChunkX\":-2,\"minChunkZ\":-1,\"maxChunkX\":1,\"maxChunkZ\":3}]}"));
        assertTrue(json.contains("\"minecraft:the_end\":{\"include\":false,\"regions\":[" + fullExclusion + "]}"));
    }

    @Test
    void selectedAreaRejectsUnsupportedCustomDimension() {
        assertThrows(IllegalArgumentException.class, () -> WorldAreaSelection.ofCorners(
                "example:custom_dimension", 0, 0, 15, 15));
    }
}
