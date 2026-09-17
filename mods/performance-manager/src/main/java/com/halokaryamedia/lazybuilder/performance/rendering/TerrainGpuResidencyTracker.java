package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.util.math.ChunkSectionPos;

/** Runtime bridge from Minecraft terrain buffers into residency and arena-planning state. */
public final class TerrainGpuResidencyTracker {
    private static final TerrainGpuResidencyLedger<VertexBuffer> LEDGER = new TerrainGpuResidencyLedger<>();
    private static final TerrainRegionAllocationRegistry<VertexBuffer> ARENAS = new TerrainRegionAllocationRegistry<>();

    private TerrainGpuResidencyTracker() {
    }

    public static void associate(VertexBuffer buffer, long sectionPos, int layerSlot) {
        if (buffer == null) return;
        if (buffer.isClosed()) {
            release(buffer);
            return;
        }

        int sectionX = ChunkSectionPos.unpackX(sectionPos);
        int sectionY = ChunkSectionPos.unpackY(sectionPos);
        int sectionZ = ChunkSectionPos.unpackZ(sectionPos);
        LEDGER.associate(buffer, sectionX, sectionY, sectionZ, layerSlot);
        ARENAS.associate(buffer, sectionX, sectionY, sectionZ, layerSlot);

        long payload = LEDGER.payloadBytes(buffer);
        if (payload > 0L) ARENAS.recordPayload(buffer, payload);
    }

    public static void recordCapacity(VertexBuffer buffer, int vertexCapacityBytes, int indexCapacityBytes) {
        if (buffer == null || buffer.isClosed()) {
            if (buffer != null) release(buffer);
            return;
        }
        LEDGER.recordCapacity(buffer, vertexCapacityBytes, indexCapacityBytes);
    }

    public static void recordPayload(VertexBuffer buffer, int vertexPayloadBytes, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordPayload(buffer, vertexPayloadBytes, indexPayloadBytes);
        ARENAS.recordPayload(buffer, LEDGER.payloadBytes(buffer));
    }

    public static void recordIndexPayload(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordIndexPayload(buffer, indexPayloadBytes);
        ARENAS.recordPayload(buffer, LEDGER.payloadBytes(buffer));
    }

    public static long capacityBytes(VertexBuffer buffer) {
        return buffer == null ? 0L : LEDGER.capacityBytes(buffer);
    }

    public static TerrainRegionAllocationRegistry.Handle allocationHandle(VertexBuffer buffer) {
        return buffer == null ? null : ARENAS.handle(buffer);
    }

    public static void release(VertexBuffer buffer) {
        if (buffer == null) return;
        LEDGER.release(buffer);
        ARENAS.release(buffer);
    }

    public static void clear() {
        LEDGER.clear();
        ARENAS.clear();
    }

    public static TerrainGpuResidencyLedger.Snapshot snapshot() {
        return LEDGER.snapshot();
    }

    public static TerrainRegionAllocationRegistry.Snapshot arenaSnapshot() {
        return ARENAS.snapshot();
    }
}
