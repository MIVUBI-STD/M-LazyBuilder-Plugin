package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Pure bookkeeping for terrain GPU residency grouped into fixed section regions.
 *
 * The ledger owns no GPU resources. It records the actual VertexBuffer-owned GPU capacities and
 * their section/region ownership so a later arena allocator can make measured decisions without
 * coupling worker meshing to GL lifetime.
 */
public final class TerrainGpuResidencyLedger<K> {
    private static final int REGION_XZ_SIZE = 8;
    private static final int REGION_Y_SIZE = 4;

    private final IdentityHashMap<K, Entry> entries = new IdentityHashMap<>();
    private long currentBytes;
    private long peakBytes;
    private long regionRelocations;

    public synchronized void associate(
            K key,
            int sectionX,
            int sectionY,
            int sectionZ,
            int layerSlot
    ) {
        if (key == null) return;

        Entry entry = entries.get(key);
        RegionKey nextRegion = regionFor(sectionX, sectionY, sectionZ);
        if (entry == null) {
            entries.put(key, new Entry(sectionX, sectionY, sectionZ, layerSlot, 0, 0));
            return;
        }

        RegionKey previousRegion = regionFor(entry.sectionX, entry.sectionY, entry.sectionZ);
        if (entry.totalBytes() > 0L && !previousRegion.equals(nextRegion)) {
            regionRelocations++;
        }
        entry.sectionX = sectionX;
        entry.sectionY = sectionY;
        entry.sectionZ = sectionZ;
        entry.layerSlot = layerSlot;
    }

    public synchronized void recordCapacity(K key, int vertexCapacityBytes, int indexCapacityBytes) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        updateBytes(entry, Math.max(0, vertexCapacityBytes), Math.max(0, indexCapacityBytes));
    }

    public synchronized void release(K key) {
        Entry removed = entries.remove(key);
        if (removed != null) {
            currentBytes -= removed.totalBytes();
            if (currentBytes < 0L) currentBytes = 0L;
        }
    }

    public synchronized void clear() {
        entries.clear();
        currentBytes = 0L;
        peakBytes = 0L;
        regionRelocations = 0L;
    }

    public synchronized Snapshot snapshot() {
        Map<RegionKey, Long> regionBytes = new HashMap<>();
        int residentBuffers = 0;

        for (Entry entry : entries.values()) {
            long bytes = entry.totalBytes();
            if (bytes <= 0L) continue;
            residentBuffers++;
            regionBytes.merge(regionFor(entry.sectionX, entry.sectionY, entry.sectionZ), bytes, Long::sum);
        }

        long largestRegionBytes = 0L;
        for (long bytes : regionBytes.values()) {
            largestRegionBytes = Math.max(largestRegionBytes, bytes);
        }

        return new Snapshot(
                currentBytes,
                peakBytes,
                residentBuffers,
                regionBytes.size(),
                largestRegionBytes,
                regionRelocations
        );
    }

    private void updateBytes(Entry entry, int vertexBytes, int indexBytes) {
        long before = entry.totalBytes();
        entry.vertexBytes = vertexBytes;
        entry.indexBytes = indexBytes;
        currentBytes += entry.totalBytes() - before;
        peakBytes = Math.max(peakBytes, currentBytes);
    }

    private static RegionKey regionFor(int sectionX, int sectionY, int sectionZ) {
        return new RegionKey(
                Math.floorDiv(sectionX, REGION_XZ_SIZE),
                Math.floorDiv(sectionY, REGION_Y_SIZE),
                Math.floorDiv(sectionZ, REGION_XZ_SIZE)
        );
    }

    private static final class Entry {
        private int sectionX;
        private int sectionY;
        private int sectionZ;
        private int layerSlot;
        private int vertexBytes;
        private int indexBytes;

        private Entry(int sectionX, int sectionY, int sectionZ, int layerSlot, int vertexBytes, int indexBytes) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.layerSlot = layerSlot;
            this.vertexBytes = vertexBytes;
            this.indexBytes = indexBytes;
        }

        private long totalBytes() {
            return (long) vertexBytes + (long) indexBytes;
        }
    }

    private record RegionKey(int x, int y, int z) {
    }

    public record Snapshot(
            long residentBytes,
            long peakResidentBytes,
            int residentBuffers,
            int residentRegions,
            long largestRegionBytes,
            long regionRelocations
    ) {
    }
}
