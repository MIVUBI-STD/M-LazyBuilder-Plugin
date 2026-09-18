package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainPerDrawShaderBackendTest {
    @Test
    void transformBufferCapacityUsesBoundedFourKiBQuanta() {
        assertEquals(0, TerrainPerDrawShaderBackend.plannedCapacity(0));
        assertEquals(4096, TerrainPerDrawShaderBackend.plannedCapacity(1));
        assertEquals(4096, TerrainPerDrawShaderBackend.plannedCapacity(4096));
        assertEquals(8192, TerrainPerDrawShaderBackend.plannedCapacity(4097));
        assertEquals(-1, TerrainPerDrawShaderBackend.plannedCapacity(Integer.MAX_VALUE));
    }
}
