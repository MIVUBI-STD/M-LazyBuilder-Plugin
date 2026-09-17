package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainGpuReclamationPolicyTest {
    @Test
    void smallBuffersAreKeptEvenAcrossRegions() {
        long small = TerrainGpuReclamationPolicy.minimumReclaimCapacityBytes() - 1L;
        assertFalse(TerrainGpuReclamationPolicy.shouldReclaim(small, 0, 0, 0, 8, 0, 0));
    }

    @Test
    void largeBuffersAreKeptInsideSameAccountingRegion() {
        long large = TerrainGpuReclamationPolicy.minimumReclaimCapacityBytes();
        assertFalse(TerrainGpuReclamationPolicy.shouldReclaim(large, 0, 0, 0, 7, 3, 7));
        assertFalse(TerrainGpuReclamationPolicy.shouldReclaim(large, -8, -4, -8, -1, -1, -1));
    }

    @Test
    void largeBuffersAreReclaimedWhenAnyAccountingRegionAxisChanges() {
        long large = TerrainGpuReclamationPolicy.minimumReclaimCapacityBytes();
        assertTrue(TerrainGpuReclamationPolicy.shouldReclaim(large, 7, 3, 7, 8, 3, 7));
        assertTrue(TerrainGpuReclamationPolicy.shouldReclaim(large, 7, 3, 7, 7, 4, 7));
        assertTrue(TerrainGpuReclamationPolicy.shouldReclaim(large, 7, 3, 7, 7, 3, 8));
    }

    @Test
    void negativeCoordinatesUseFloorDivRegionSemantics() {
        long large = TerrainGpuReclamationPolicy.minimumReclaimCapacityBytes();
        assertFalse(TerrainGpuReclamationPolicy.shouldReclaim(large, -8, -4, -8, -1, -1, -1));
        assertTrue(TerrainGpuReclamationPolicy.shouldReclaim(large, -1, -1, -1, 0, 0, 0));
    }
}
