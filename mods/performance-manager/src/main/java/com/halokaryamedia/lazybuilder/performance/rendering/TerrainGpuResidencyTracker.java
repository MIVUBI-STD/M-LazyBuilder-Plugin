package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.util.math.ChunkSectionPos;

/** Runtime bridge from Minecraft terrain buffers into residency, arena, and draw-state ownership. */
public final class TerrainGpuResidencyTracker {
    private static final TerrainGpuResidencyLedger<VertexBuffer> LEDGER = new TerrainGpuResidencyLedger<>();
    private static final TerrainRegionAllocationRegistry<VertexBuffer> ARENAS = new TerrainRegionAllocationRegistry<>();
    private static final TerrainArenaDrawStateRegistry DRAW_STATES = new TerrainArenaDrawStateRegistry();

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

        long arenaPayload = DRAW_STATES.requiredAllocationBytes(buffer);
        if (arenaPayload <= 0L) arenaPayload = LEDGER.payloadBytes(buffer);
        if (arenaPayload > 0L) ARENAS.recordPayload(buffer, arenaPayload);
    }

    public static void recordCapacity(VertexBuffer buffer, int vertexCapacityBytes, int indexCapacityBytes) {
        if (buffer == null || buffer.isClosed()) {
            if (buffer != null) release(buffer);
            return;
        }
        LEDGER.recordCapacity(buffer, vertexCapacityBytes, indexCapacityBytes);
    }

    public static void recordDrawState(
            VertexBuffer buffer,
            BuiltBuffer.DrawParameters parameters,
            int vertexPayloadBytes,
            int indexPayloadBytes
    ) {
        if (buffer == null || buffer.isClosed()) return;
        DRAW_STATES.recordVertexUpload(buffer, parameters, vertexPayloadBytes, indexPayloadBytes);
    }

    public static void recordPayload(VertexBuffer buffer, int vertexPayloadBytes, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordPayload(buffer, vertexPayloadBytes, indexPayloadBytes);
        long arenaPayload = DRAW_STATES.requiredAllocationBytes(buffer);
        if (arenaPayload <= 0L) arenaPayload = LEDGER.payloadBytes(buffer);
        ARENAS.recordPayload(buffer, arenaPayload);
    }

    public static void recordIndexDrawState(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        DRAW_STATES.recordIndexUpload(buffer, indexPayloadBytes);
    }

    public static void recordIndexPayload(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordIndexPayload(buffer, indexPayloadBytes);
        long arenaPayload = DRAW_STATES.requiredAllocationBytes(buffer);
        if (arenaPayload <= 0L) arenaPayload = LEDGER.payloadBytes(buffer);
        ARENAS.recordPayload(buffer, arenaPayload);
    }

    public static long capacityBytes(VertexBuffer buffer) {
        return buffer == null ? 0L : LEDGER.capacityBytes(buffer);
    }

    public static TerrainRegionAllocationRegistry.Handle allocationHandle(VertexBuffer buffer) {
        return buffer == null ? null : ARENAS.handle(buffer);
    }

    public static TerrainArenaDrawStateRegistry.DrawState drawState(VertexBuffer buffer) {
        return buffer == null ? null : DRAW_STATES.state(buffer);
    }

    public static TerrainArenaDrawPlanner.Command drawCommand(VertexBuffer buffer) {
        if (buffer == null) return null;
        return new TerrainArenaDrawPlanner.Command(ARENAS.handle(buffer), DRAW_STATES.state(buffer));
    }

    public static void release(VertexBuffer buffer) {
        if (buffer == null) return;
        TerrainPhysicalArenaManager.release(buffer);
        LEDGER.release(buffer);
        ARENAS.release(buffer);
        DRAW_STATES.release(buffer);
    }

    public static void clear() {
        TerrainPhysicalArenaManager.clear();
        TerrainDrawTransformStream.clear();
        TerrainArenaDrawDiagnostics.clear();
        LEDGER.clear();
        ARENAS.clear();
        DRAW_STATES.clear();
    }

    public static TerrainGpuResidencyLedger.Snapshot snapshot() {
        return LEDGER.snapshot();
    }

    public static TerrainRegionAllocationRegistry.Snapshot arenaSnapshot() {
        return ARENAS.snapshot();
    }

    public static TerrainPhysicalArenaManager.Snapshot physicalArenaSnapshot() {
        return TerrainPhysicalArenaManager.snapshot();
    }
}
