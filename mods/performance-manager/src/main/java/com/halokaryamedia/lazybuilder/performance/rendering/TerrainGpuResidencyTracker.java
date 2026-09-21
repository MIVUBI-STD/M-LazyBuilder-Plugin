package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Runtime bridge from Minecraft terrain buffers into residency, arena, and draw-state ownership. */
public final class TerrainGpuResidencyTracker {
    private static final TerrainGpuResidencyLedger<VertexBuffer> LEDGER = new TerrainGpuResidencyLedger<>();
    private static final TerrainRegionAllocationRegistry<VertexBuffer> ARENAS = new TerrainRegionAllocationRegistry<>();
    private static final TerrainArenaDrawStateRegistry DRAW_STATES = new TerrainArenaDrawStateRegistry();
    // GPU residency and draw-state mutation both occur on the render/upload owner.
    // Cache immutable command projections so visible-section traversal does not
    // allocate one command record per section per frame.
    private static final IdentityHashMap<VertexBuffer, TerrainArenaDrawPlanner.Command> DRAW_COMMANDS =
            new IdentityHashMap<>();
    private static volatile Object sessionOwner;
    private static volatile long sessionGeneration;
    private static final AtomicLong TERRAIN_CONTENT_REVISION = new AtomicLong();
    private static volatile String sessionStatus = "unowned";

    private TerrainGpuResidencyTracker() {
    }

    /**
     * Transfers terrain ownership to a new ChunkBuilder/session. Existing state is cleared first so
     * buffers from different renderer lifecycles can never share one logical/physical arena graph.
     */
    public static synchronized boolean claimSession(Object owner) {
        if (owner == null) return false;
        if (sessionOwner == owner) return true;

        if (sessionOwner != null && !clearSafely()) {
            sessionStatus = "handoff-blocked:recovery-failed";
            return false;
        }

        sessionOwner = owner;
        sessionGeneration++;
        sessionStatus = "active";
        return true;
    }

    /** Clears only if the caller still owns the active terrain session. */
    public static synchronized boolean clearSession(Object owner) {
        if (owner == null || sessionOwner != owner) return false;
        if (!clearSafely()) {
            sessionStatus = "clear-blocked:recovery-failed";
            return false;
        }
        sessionOwner = null;
        sessionStatus = "unowned";
        return true;
    }

    /**
     * Clears and rotates generation while retaining the same ChunkBuilder as owner.
     * Queued work tagged with the previous generation becomes stale immediately.
     */
    public static synchronized boolean restartSession(Object owner) {
        if (owner == null) return false;
        if (sessionOwner != owner) return claimSession(owner);
        if (!clearSafely()) {
            sessionStatus = "restart-blocked:recovery-failed";
            return false;
        }
        sessionGeneration++;
        sessionStatus = "active";
        return true;
    }

    public static long sessionGeneration(Object owner) {
        return owner != null && sessionOwner == owner ? sessionGeneration : -1L;
    }

    public static boolean ownsSession(Object owner) {
        return owner != null && sessionOwner == owner;
    }

    public static boolean ownsSession(Object owner, long generation) {
        return owner != null
                && generation >= 0L
                && sessionOwner == owner
                && sessionGeneration == generation;
    }

    public static SessionSnapshot sessionSnapshot() {
        return new SessionSnapshot(sessionGeneration, sessionOwner != null, sessionStatus);
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
        TerrainRegionAllocationRegistry.Handle current = ARENAS.handle(buffer);
        TerrainRegionAllocationRegistry.ArenaKey next = TerrainRegionAllocationRegistry.arenaFor(
                sectionX, sectionY, sectionZ, layerSlot
        );
        if (current != null && !current.arenaKey().equals(next)
                && !TerrainPhysicalArenaManager.recoverVanillaBacking(buffer)) {
            return;
        }
        LEDGER.associate(buffer, sectionX, sectionY, sectionZ, layerSlot);
        ARENAS.associate(buffer, sectionX, sectionY, sectionZ, layerSlot);
        markTerrainContentChanged();
        invalidateAllDrawCommands();

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
        markTerrainContentChanged();
        invalidateDrawCommand(buffer);
    }

    public static void recordPayload(VertexBuffer buffer, int vertexPayloadBytes, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordPayload(buffer, vertexPayloadBytes, indexPayloadBytes);
        long arenaPayload = DRAW_STATES.requiredAllocationBytes(buffer);
        if (arenaPayload <= 0L) arenaPayload = LEDGER.payloadBytes(buffer);
        ARENAS.recordPayload(buffer, arenaPayload);
        markTerrainContentChanged();
        invalidateAllDrawCommands();
    }

    public static void recordIndexDrawState(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        DRAW_STATES.recordIndexUpload(buffer, indexPayloadBytes);
        markTerrainContentChanged();
        invalidateDrawCommand(buffer);
    }

    public static void recordIndexPayload(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null || buffer.isClosed()) return;
        LEDGER.recordIndexPayload(buffer, indexPayloadBytes);
        long arenaPayload = DRAW_STATES.requiredAllocationBytes(buffer);
        if (arenaPayload <= 0L) arenaPayload = LEDGER.payloadBytes(buffer);
        ARENAS.recordPayload(buffer, arenaPayload);
        markTerrainContentChanged();
        invalidateAllDrawCommands();
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
        TerrainArenaDrawPlanner.Command cached = DRAW_COMMANDS.get(buffer);
        if (cached != null) return cached;

        TerrainRegionAllocationRegistry.Handle handle = ARENAS.handle(buffer);
        TerrainArenaDrawStateRegistry.DrawState state = DRAW_STATES.state(buffer);
        if (handle == null && state == null) return null;

        TerrainArenaDrawPlanner.Command command = new TerrainArenaDrawPlanner.Command(handle, state);
        DRAW_COMMANDS.put(buffer, command);
        return command;
    }

    public static void release(VertexBuffer buffer) {
        if (buffer == null) return;
        DRAW_COMMANDS.remove(buffer);
        markTerrainContentChanged();
        TerrainPhysicalArenaManager.release(buffer);
        LEDGER.release(buffer);
        ARENAS.release(buffer);
        DRAW_STATES.release(buffer);
    }

    public static void clear() {
        clearSafely();
    }

    /**
     * Clears logical terrain ownership only when physical arena ownership has been released safely.
     * If exclusive vanilla-backing recovery fails, logical state is retained for retry/fallback.
     */
    public static boolean clearSafely() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(TerrainGpuResidencyTracker::clearSafely);
            return false;
        }

        TerrainMultiDrawSubmissionBackend.clear();
        TerrainPerDrawShaderBackend.clear();
        if (!TerrainPhysicalArenaManager.clearSafely()) {
            return false;
        }
        TerrainDrawTransformStream.clear();
        TerrainArenaDrawDiagnostics.clear();
        DRAW_COMMANDS.clear();
        TERRAIN_CONTENT_REVISION.incrementAndGet();
        LEDGER.clear();
        ARENAS.clear();
        DRAW_STATES.clear();
        return true;
    }

    public static long contentRevision() {
        return TERRAIN_CONTENT_REVISION.get();
    }

    private static void markTerrainContentChanged() {
        TERRAIN_CONTENT_REVISION.incrementAndGet();
    }

    private static void invalidateDrawCommand(VertexBuffer buffer) {
        if (buffer != null) DRAW_COMMANDS.remove(buffer);
    }

    private static void invalidateAllDrawCommands() {
        DRAW_COMMANDS.clear();
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

    public record SessionSnapshot(long generation, boolean owned, String status) {
        public SessionSnapshot {
            status = status == null ? "" : status;
        }
    }
}
