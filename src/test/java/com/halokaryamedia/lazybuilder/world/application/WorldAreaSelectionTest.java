package com.halokaryamedia.lazybuilder.world.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldAreaSelectionTest {
    @TempDir Path tempDir;

    @Test
    void cornersNormalizeAndNegativeBlockCoordinatesUseFloorChunkMath() {
        WorldAreaSelection area = WorldAreaSelection.ofCorners(31, 48, -17, -1);

        assertEquals(-17, area.minBlockX());
        assertEquals(-1, area.minBlockZ());
        assertEquals(31, area.maxBlockX());
        assertEquals(48, area.maxBlockZ());
        assertEquals(-2, area.minChunkX());
        assertEquals(-1, area.minChunkZ());
        assertEquals(1, area.maxChunkX());
        assertEquals(3, area.maxChunkZ());
        assertEquals(20L, area.chunkCount());
    }

    @Test
    void pruningDocumentUsesIncludeBoundsForAllVanillaDimensions() throws Exception {
        WorldAreaSelection area = WorldAreaSelection.ofCorners(-17, -1, 31, 48);
        Path pruning = WorldExportService.writeAreaPruning(area, tempDir);
        String json = Files.readString(pruning);

        assertTrue(json.contains("\"minecraft:overworld\""));
        assertTrue(json.contains("\"minecraft:the_nether\""));
        assertTrue(json.contains("\"minecraft:the_end\""));
        assertTrue(json.contains("\"include\":true"));
        assertTrue(json.contains("\"minChunkX\":-2"));
        assertTrue(json.contains("\"minChunkZ\":-1"));
        assertTrue(json.contains("\"maxChunkX\":1"));
        assertTrue(json.contains("\"maxChunkZ\":3"));
    }
}
