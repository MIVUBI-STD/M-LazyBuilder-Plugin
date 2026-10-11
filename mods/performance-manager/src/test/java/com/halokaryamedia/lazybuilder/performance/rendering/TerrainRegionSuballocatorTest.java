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
    void failedReplacementKeepsPreviousAllocationAndAccounting() {
        TerrainRegionSuballocator<String> allocator = new TerrainRegionSuballocator<>(1024L);
        var previous = allocator.allocate("a", 512L);
        allocator.allocate("b", 512L);

        assertNull(allocator.allocate("a", 768L));
        var state = allocator.snapshot();
        assertEquals(1024L, state.usedBytes());
        assertEquals(2, state.allocationCount());
        assertEquals(0L, state.freeBytes());

        allocator.release("b");
        var next = allocator.allocate("c", 512L);
        assertNotNull(next);
        assertEquals(previous.offsetBytes() + previous.sizeBytes(), next.offsetBytes());
        assertEquals(2, allocator.snapshot().allocationCount());
    }

    @Test
    void replacementCanUseFreeSpansAdjacentToOriginalBlock() {
        TerrainRegionSuballocator<String> allocator = new TerrainRegionSuballocator<>(1024L);
        allocator.allocate("a", 256L);
        allocator.allocate("b", 256L);
        allocator.allocate("c", 256L);
        allocator.allocate("d", 256L);

        allocator.release("b");
        var grown = allocator.allocate("a", 512L);
        assertNotNull(grown);
        assertEquals(0L, grown.offsetBytes());
        assertEquals(512L, grown.sizeBytes());
        assertEquals(1024L, allocator.snapshot().usedBytes());
        assertEquals(3, allocator.snapshot().allocationCount());
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
