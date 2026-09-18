package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuBufferGrowthPolicyTest {
    @Test
    void existingCapacityIsNeverShrunk() {
        assertEquals(8192, GpuBufferGrowthPolicy.capacityFor(8192, 4096));
        assertEquals(8192, GpuBufferGrowthPolicy.capacityFor(8192, 8192));
    }

    @Test
    void growthAddsBoundedHeadroomAndAlignment() {
        int capacity = GpuBufferGrowthPolicy.capacityFor(4096, 5000);
        assertTrue(capacity >= 5000);
        assertEquals(0, capacity % 4096);
    }

    @Test
    void largeGrowthDoesNotAddMoreThanOneMegabyteHeadroomPlusAlignment() {
        int required = 16 * 1024 * 1024;
        int capacity = GpuBufferGrowthPolicy.capacityFor(0, required);
        assertTrue(capacity >= required);
        assertTrue(capacity <= required + 1024 * 1024 + 4095);
    }
}
