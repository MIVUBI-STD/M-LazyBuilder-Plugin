package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.util.math.ChunkSectionPos;

/** Runtime bridge from Minecraft terrain buffers into the pure residency ledger. */
public final class TerrainGpuResidencyTracker {
    private static final TerrainGpuResidencyLedger<VertexBuffer> LEDGER = new TerrainGpuResidencyLedger<>();

    private TerrainGpuResidencyTracker() {
    }

    public static void associate(VertexBuffer buffer, long sectionPos, int layerSlot) {
        if (buffer == null) return;
        if (buffer.isClosed()) {
            LEDGER.release(buffer);
            return;
        }
        LEDGER.associate(
                buffer,
                ChunkSectionPos.unpackX(sectionPos),
                ChunkSectionPos.unpackY(sectionPos),
                ChunkSectionPos.unpackZ(sectionPos),
                layerSlot
        );
    }

    public static void recordCapacity(VertexBuffer buffer, int vertexCapacityBytes, int indexCapacityBytes) {
        if (buffer == null || buffer.isClosed()) {
            if (buffer != null) LEDGER.release(buffer);
            return;
        }
        LEDGER.recordCapacity(buffer, vertexCapacityBytes, indexCapacityBytes);
    }

    public static void release(VertexBuffer buffer) {
        if (buffer != null) LEDGER.release(buffer);
    }

    public static void clear() {
        LEDGER.clear();
    }

    public static TerrainGpuResidencyLedger.Snapshot snapshot() {
        return LEDGER.snapshot();
    }
}
