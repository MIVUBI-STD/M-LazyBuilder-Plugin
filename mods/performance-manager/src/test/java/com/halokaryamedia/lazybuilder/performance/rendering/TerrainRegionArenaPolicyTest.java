package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainRegionArenaPolicyTest {
    @Test
    void alignsSuballocationsTo256Bytes() {
        assertEquals(0L, TerrainRegionArenaPolicy.alignedSize(0L));
        assertEquals(256L, TerrainRegionArenaPolicy.alignedSize(1L));
        assertEquals(256L, TerrainRegionArenaPolicy.alignedSize(256L));
        assertEquals(512L, TerrainRegionArenaPolicy.alignedSize(257L));
    }

    @Test
    void plansMiBQuantizedCapacityWithHeadroom() {
        assertEquals(0L, TerrainRegionArenaPolicy.plannedCapacity(0L));
        assertEquals(1L << 20, TerrainRegionArenaPolicy.plannedCapacity(256L << 10));
        assertEquals(3L << 20, TerrainRegionArenaPolicy.plannedCapacity(2L << 20));
        assertEquals(7L << 20, TerrainRegionArenaPolicy.plannedCapacity(5L << 20));
    }

    @Test
    void compactionRequiresAtLeastTwoMiBRecoverableCapacity() {
        long payload = 2L << 20;
        assertFalse(TerrainRegionArenaPolicy.shouldCompact(4L << 20, payload));
        assertTrue(TerrainRegionArenaPolicy.shouldCompact(5L << 20, payload));
        assertEquals(2L << 20, TerrainRegionArenaPolicy.potentialReclaim(5L << 20, payload));
    }
}
