package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TerrainRegionSuballocatorTest {
    @Test
    void allocatesAlignedFirstFitSpans() {
        TerrainRegionSuballocator<String> allocator = new TerrainRegionSuballocator<>(2048L);

        var first = allocator.allocate("a", 1L);
        var second = allocator.allocate("b", 300L);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(0L, first.offsetBytes());
        assertEquals(256L, first.sizeBytes());
        assertEquals(256L, second.offsetBytes());
        assertEquals(512L, second.sizeBytes());
        assertEquals(768L, allocator.snapshot().usedBytes());
    }

    @Test
    void releaseCoalescesAdjacentFreeSpans() {
        TerrainRegionSuballocator<String> allocator = new TerrainRegionSuballocator<>(1024L);
        allocator.allocate("a", 256L);
        allocator.allocate("b", 256L);
        allocator.allocate("c", 256L);

        allocator.release("b");
        allocator.release("a");
        allocator.release("c");

        var snapshot = allocator.snapshot();
        assertEquals(1024L, snapshot.freeBytes());
        assertEquals(1024L, snapshot.largestFreeSpanBytes());
        assertEquals(1, snapshot.freeSpanCount());
        assertEquals(0L, snapshot.fragmentedFreeBytes());
    }

    @Test
    void fragmentationCanPreventAllocationUntilSpansCoalesce() {
        TerrainRegionSuballocator<String> allocator = new TerrainRegionSuballocator<>(1024L);
        allocator.allocate("a", 256L);
        allocator.allocate("b", 256L);
        allocator.allocate("c", 256L);
        allocator.allocate("d", 256L);
        allocator.release("b");
        allocator.release("d");

        assertNull(allocator.allocate("large", 512L));
        assertEquals(256L, allocator.snapshot().fragmentedFreeBytes());

        allocator.release("c");
        assertNotNull(allocator.allocate("large", 512L));
    }
}
