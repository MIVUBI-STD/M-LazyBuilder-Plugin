package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure aligned first-fit allocator model for one future shared terrain GPU arena.
 *
 * It owns no GPU resource. Free spans are coalesced so the physical arena implementation can reuse
 * the exact same allocation semantics without coupling tests to OpenGL/render-thread state.
 */
public final class TerrainRegionSuballocator<K> {
    private final long capacityBytes;
    private final TreeMap<Long, Long> freeSpans = new TreeMap<>();
    private final Map<K, Allocation> allocations = new HashMap<>();
    private long usedBytes;

    public TerrainRegionSuballocator(long capacityBytes) {
        this.capacityBytes = Math.max(0L, capacityBytes);
        if (this.capacityBytes > 0L) freeSpans.put(0L, this.capacityBytes);
    }

    public synchronized Allocation allocate(K key, long payloadBytes) {
        if (key == null || payloadBytes <= 0L) return null;
        release(key);

        long size = TerrainRegionArenaPolicy.alignedSize(payloadBytes);
        long selectedOffset = -1L;
        long selectedLength = 0L;
        for (Map.Entry<Long, Long> span : freeSpans.entrySet()) {
            if (span.getValue() >= size) {
                selectedOffset = span.getKey();
                selectedLength = span.getValue();
                break;
            }
        }
        if (selectedOffset < 0L) return null;

        long remaining = selectedLength - size;
        freeSpans.remove(selectedOffset);
        if (remaining > 0L) freeSpans.put(selectedOffset + size, remaining);

        Allocation allocation = new Allocation(selectedOffset, size, payloadBytes);
        allocations.put(key, allocation);
        usedBytes += size;
        return allocation;
    }

    public synchronized void release(K key) {
        Allocation removed = allocations.remove(key);
        if (removed == null) return;

        usedBytes -= removed.sizeBytes();
        if (usedBytes < 0L) usedBytes = 0L;
        insertFreeSpan(removed.offsetBytes(), removed.sizeBytes());
    }

    public synchronized Snapshot snapshot() {
        long largestFree = 0L;
        for (long span : freeSpans.values()) largestFree = Math.max(largestFree, span);
        long free = Math.max(0L, capacityBytes - usedBytes);
        return new Snapshot(
                capacityBytes,
                usedBytes,
                free,
                largestFree,
                allocations.size(),
                freeSpans.size()
        );
    }

    private void insertFreeSpan(long offset, long size) {
        long start = offset;
        long length = size;

        Map.Entry<Long, Long> lower = freeSpans.floorEntry(start);
        if (lower != null && lower.getKey() + lower.getValue() == start) {
            start = lower.getKey();
            length += lower.getValue();
            freeSpans.remove(lower.getKey());
        }

        Map.Entry<Long, Long> higher = freeSpans.ceilingEntry(start);
        if (higher != null && start + length == higher.getKey()) {
            length += higher.getValue();
            freeSpans.remove(higher.getKey());
        }

        freeSpans.put(start, length);
    }

    public record Allocation(long offsetBytes, long sizeBytes, long payloadBytes) {
    }

    public record Snapshot(
            long capacityBytes,
            long usedBytes,
            long freeBytes,
            long largestFreeSpanBytes,
            int allocationCount,
            int freeSpanCount
    ) {
        public long fragmentedFreeBytes() {
            return Math.max(0L, freeBytes - largestFreeSpanBytes);
        }
    }
}
