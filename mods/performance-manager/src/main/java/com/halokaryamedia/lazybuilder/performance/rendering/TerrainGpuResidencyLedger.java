package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Pure bookkeeping for terrain GPU residency grouped into fixed section regions.
 *
 * The ledger owns no GPU resources. It records actual VertexBuffer-owned GPU capacities, uploaded
 * payload sizes, and section/region ownership so physical arena work can use measured pressure.
 */
public final class TerrainGpuResidencyLedger<K> {
    private static final int REGION_XZ_SIZE = 8;
    private static final int REGION_Y_SIZE = 4;

    private final IdentityHashMap<K, Entry> entries = new IdentityHashMap<>();
    private long currentBytes;
    private long currentPayloadBytes;
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
            entries.put(key, new Entry(sectionX, sectionY, sectionZ, layerSlot));
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

        long before = entry.totalBytes();
        entry.vertexCapacityBytes = Math.max(0, vertexCapacityBytes);
        entry.indexCapacityBytes = Math.max(0, indexCapacityBytes);
        currentBytes += entry.totalBytes() - before;
        peakBytes = Math.max(peakBytes, currentBytes);
    }

    public synchronized void recordPayload(K key, int vertexPayloadBytes, int indexPayloadBytes) {
        Entry entry = entries.get(key);
        if (entry == null) return;

        long before = entry.totalPayloadBytes();
        entry.vertexPayloadBytes = Math.max(0, vertexPayloadBytes);
        entry.indexPayloadBytes = Math.max(0, indexPayloadBytes);
        currentPayloadBytes += entry.totalPayloadBytes() - before;
    }

    public synchronized void recordIndexPayload(K key, int indexPayloadBytes) {
        Entry entry = entries.get(key);
        if (entry == null) return;

        long before = entry.totalPayloadBytes();
        entry.indexPayloadBytes = Math.max(0, indexPayloadBytes);
        currentPayloadBytes += entry.totalPayloadBytes() - before;
    }

    public synchronized long capacityBytes(K key) {
        Entry entry = entries.get(key);
        return entry == null ? 0L : entry.totalBytes();
    }

    public synchronized void release(K key) {
        Entry removed = entries.remove(key);
        if (removed != null) {
            currentBytes -= removed.totalBytes();
            currentPayloadBytes -= removed.totalPayloadBytes();
            if (currentBytes < 0L) currentBytes = 0L;
            if (currentPayloadBytes < 0L) currentPayloadBytes = 0L;
        }
    }

    public synchronized void clear() {
        entries.clear();
        currentBytes = 0L;
        currentPayloadBytes = 0L;
        peakBytes = 0L;
        regionRelocations = 0L;
    }

    public synchronized Snapshot snapshot() {
        Map<RegionKey, RegionTotals> regionTotals = new HashMap<>();
        int residentBuffers = 0;

        for (Entry entry : entries.values()) {
            long bytes = entry.totalBytes();
            if (bytes <= 0L) continue;

            residentBuffers++;
            RegionTotals totals = regionTotals.computeIfAbsent(
                    regionFor(entry.sectionX, entry.sectionY, entry.sectionZ),
                    ignored -> new RegionTotals()
            );
            totals.capacityBytes += bytes;
            totals.payloadBytes += entry.totalPayloadBytes();
        }

        long largestRegionBytes = 0L;
        long largestRegionHeadroomBytes = 0L;
        for (RegionTotals totals : regionTotals.values()) {
            largestRegionBytes = Math.max(largestRegionBytes, totals.capacityBytes);
            largestRegionHeadroomBytes = Math.max(
                    largestRegionHeadroomBytes,
                    Math.max(0L, totals.capacityBytes - totals.payloadBytes)
            );
        }

        long headroomBytes = Math.max(0L, currentBytes - currentPayloadBytes);
        return new Snapshot(
                currentBytes,
                currentPayloadBytes,
                headroomBytes,
                peakBytes,
                residentBuffers,
                regionTotals.size(),
                largestRegionBytes,
                largestRegionHeadroomBytes,
                regionRelocations
        );
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
        private int vertexCapacityBytes;
        private int indexCapacityBytes;
        private int vertexPayloadBytes;
        private int indexPayloadBytes;

        private Entry(int sectionX, int sectionY, int sectionZ, int layerSlot) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.layerSlot = layerSlot;
        }

        private long totalBytes() {
            return (long) vertexCapacityBytes + (long) indexCapacityBytes;
        }

        private long totalPayloadBytes() {
            return (long) vertexPayloadBytes + (long) indexPayloadBytes;
        }
    }

    private static final class RegionTotals {
        private long capacityBytes;
        private long payloadBytes;
    }

    private record RegionKey(int x, int y, int z) {
    }

    public record Snapshot(
            long residentBytes,
            long payloadBytes,
            long headroomBytes,
            long peakResidentBytes,
            int residentBuffers,
            int residentRegions,
            long largestRegionBytes,
            long largestRegionHeadroomBytes,
            long regionRelocations
    ) {
    }
}
