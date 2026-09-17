package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlBufferTarget;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Render-thread physical VBO/EBO backing for live terrain arena allocations. */
public final class TerrainPhysicalArenaManager {
    private static final Map<TerrainRegionAllocationRegistry.ArenaKey, Arena> ARENAS = new HashMap<>();
    private static final IdentityHashMap<VertexBuffer, Resident> RESIDENTS = new IdentityHashMap<>();

    private static Arena boundArena;
    private static int boundVao = -1;
    private static Prepared prepared;

    private static long uploadedBytes;
    private static long physicalDraws;
    private static long customIndexDraws;
    private static long physicalBufferBinds;
    private static long physicalBindReuses;
    private static long arenaResizes;
    private static long invalidations;

    private TerrainPhysicalArenaManager() {
    }

    public static boolean upload(VertexBuffer source, ByteBuffer vertices, ByteBuffer customIndices) {
        if (source == null || vertices == null || !RenderSystem.isOnRenderThread()) return false;

        TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
        int vertexBytes = vertices.remaining();
        int vertexCapacity = TerrainPhysicalArenaPolicy.plannedVertexCapacity(command, vertexBytes);
        if (vertexCapacity < 0) {
            invalidate(source);
            return false;
        }

        int indexBytes = customIndices == null ? 0 : customIndices.remaining();
        int indexCapacity = 0;
        if (indexBytes > 0) {
            indexCapacity = TerrainPhysicalArenaPolicy.plannedIndexCapacity(command, indexBytes);
            if (indexCapacity < 0) {
                invalidate(source);
                return false;
            }
        }

        TerrainRegionAllocationRegistry.Handle handle = command.handle();
        TerrainRegionAllocationRegistry.ArenaKey key = handle.arenaKey();
        Arena arena = ARENAS.get(key);
        if (arena == null) {
            arena = new Arena(vertexCapacity, indexCapacity);
            ARENAS.put(key, arena);
        } else if (needsResize(arena, vertexCapacity, indexCapacity)) {
            invalidateArenaResidents(arena);
            if (arena.vertexBuffer.size < vertexCapacity) arena.vertexBuffer.resize(vertexCapacity);
            if (indexCapacity > 0) ensureIndexBuffer(arena, indexCapacity, true);
            arena.epoch++;
            arenaResizes++;
            noteExternalBind();
        } else if (indexCapacity > 0) {
            ensureIndexBuffer(arena, indexCapacity, false);
        }

        long vertexOffset = command.vertexByteOffset();
        if (vertexOffset < 0L || vertexOffset > Integer.MAX_VALUE) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        Resident previous = RESIDENTS.get(source);
        if (previous != null && !previous.arenaKey.equals(key)) detachResident(source, previous);

        try {
            arena.vertexBuffer.copyFrom(vertices.duplicate(), (int) vertexOffset);
            uploadedBytes += vertexBytes;

            if (indexBytes > 0) {
                long indexOffset = command.indexByteOffset();
                if (arena.indexBuffer == null || indexOffset < 0L || indexOffset > Integer.MAX_VALUE) {
                    invalidate(source);
                    discardArenaIfUnused(key, arena);
                    return false;
                }
                arena.indexBuffer.copyFrom(customIndices.duplicate(), (int) indexOffset);
                uploadedBytes += indexBytes;
            }
        } catch (RuntimeException ex) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        arena.sources.put(source, Boolean.TRUE);
        RESIDENTS.put(source, new Resident(key, handle.generation(), arena.epoch, vertexBytes, indexBytes));
        prepared = null;
        return true;
    }

    /** Update a custom/sorted index range without moving the existing vertex mirror. */
    public static boolean uploadIndex(VertexBuffer source, ByteBuffer indices) {
        if (source == null || indices == null || !RenderSystem.isOnRenderThread()) return false;

        TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
        int indexBytes = indices.remaining();
        if (!TerrainPhysicalArenaIndexPolicy.isCustomIndexReady(command)
                || command.state().indexPayloadBytes() != indexBytes) {
            invalidate(source);
            return false;
        }

        Resident resident = RESIDENTS.get(source);
        if (!matchesLogicalHandle(command, resident)) {
            invalidate(source);
            return false;
        }

        Arena arena = ARENAS.get(resident.arenaKey);
        int requiredCapacity = TerrainPhysicalArenaPolicy.plannedIndexCapacity(command, indexBytes);
        if (arena == null || arena.indexBuffer == null || requiredCapacity < 0 || arena.indexBuffer.size < requiredCapacity) {
            invalidate(source);
            return false;
        }

        try {
            arena.indexBuffer.copyFrom(indices.duplicate(), (int) command.indexByteOffset());
        } catch (RuntimeException ex) {
            invalidate(source);
            return false;
        }

        RESIDENTS.put(source, new Resident(
                resident.arenaKey,
                resident.handleGeneration,
                resident.arenaEpoch,
                resident.vertexBytes,
                indexBytes
        ));
        uploadedBytes += indexBytes;
        prepared = null;
        return true;
    }

    public static boolean bind(VertexBuffer source) {
        if (source == null || !RenderSystem.isOnRenderThread()) return false;

        TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
        Resident resident = RESIDENTS.get(source);
        if (!matches(command, resident)) {
            invalidate(source);
            return false;
        }

        Arena arena = ARENAS.get(resident.arenaKey);
        if (arena == null || resident.arenaEpoch != arena.epoch) {
            invalidate(source);
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        VaoState vao = arena.vaos.get(state.format());
        if (vao == null) {
            vao = createVao(arena, state.format());
            arena.vaos.put(state.format(), vao);
        } else if (boundArena == arena && boundVao == vao.id) {
            physicalBindReuses++;
        } else {
            BufferRenderer.resetCurrentVertexBuffer();
            GlStateManager._glBindVertexArray(vao.id);
            boundArena = arena;
            boundVao = vao.id;
            physicalBufferBinds++;
        }

        VertexFormat.IndexType drawIndexType;
        long drawIndexOffset;
        if (state.indexPayloadBytes() > 0) {
            if (arena.indexBuffer == null || resident.indexBytes != state.indexPayloadBytes()) {
                invalidate(source);
                return false;
            }
            if (!vao.customIndexBound) {
                arena.indexBuffer.bind();
                vao.customIndexBound = true;
                vao.sequentialMode = null;
            }
            drawIndexType = state.indexType();
            drawIndexOffset = command.indexByteOffset();
        } else {
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(state.mode());
            if (vao.customIndexBound
                    || vao.sequentialMode != state.mode()
                    || !sequential.isLargeEnough(state.indexCount())) {
                sequential.bindAndGrow(state.indexCount());
                vao.customIndexBound = false;
                vao.sequentialMode = state.mode();
            }
            drawIndexType = sequential.getIndexType();
            drawIndexOffset = 0L;
        }

        prepared = new Prepared(source, resident, drawIndexType, drawIndexOffset);
        return true;
    }

    public static boolean draw(VertexBuffer source) {
        if (source == null || !RenderSystem.isOnRenderThread()) return false;
        Prepared current = prepared;
        if (current == null || current.source != source) return false;

        Resident resident = RESIDENTS.get(source);
        TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
        if (resident != current.resident || !matches(command, resident)) {
            prepared = null;
            invalidate(source);
            return false;
        }

        int baseVertex = TerrainPhysicalArenaPolicy.baseVertex(command);
        if (baseVertex < 0) {
            prepared = null;
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        GL32C.glDrawElementsBaseVertex(
                state.mode().glMode,
                state.indexCount(),
                current.indexType.glType,
                current.indexByteOffset,
                baseVertex
        );
        physicalDraws++;
        if (state.indexPayloadBytes() > 0) customIndexDraws++;
        prepared = null;
        return true;
    }

    public static void noteExternalBind() {
        boundArena = null;
        boundVao = -1;
        prepared = null;
    }

    public static void release(VertexBuffer source) {
        if (source == null) return;
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> release(source));
            return;
        }
        Resident resident = RESIDENTS.remove(source);
        if (resident != null) detachResident(source, resident);
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(TerrainPhysicalArenaManager::clear);
            return;
        }

        noteExternalBind();
        for (Arena arena : ARENAS.values()) closeArena(arena);
        ARENAS.clear();
        RESIDENTS.clear();
        uploadedBytes = 0L;
        physicalDraws = 0L;
        customIndexDraws = 0L;
        physicalBufferBinds = 0L;
        physicalBindReuses = 0L;
        arenaResizes = 0L;
        invalidations = 0L;
    }

    public static Snapshot snapshot() {
        long bytes = 0L;
        for (Arena arena : ARENAS.values()) {
            bytes += Math.max(0, arena.vertexBuffer.size);
            if (arena.indexBuffer != null) bytes += Math.max(0, arena.indexBuffer.size);
        }
        return new Snapshot(
                bytes,
                ARENAS.size(),
                RESIDENTS.size(),
                uploadedBytes,
                physicalDraws,
                customIndexDraws,
                physicalBufferBinds,
                physicalBindReuses,
                arenaResizes,
                invalidations
        );
    }

    private static VaoState createVao(Arena arena, VertexFormat format) {
        BufferRenderer.resetCurrentVertexBuffer();
        int id = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(id);
        arena.vertexBuffer.bind();
        format.setupState();
        boundArena = arena;
        boundVao = id;
        physicalBufferBinds++;
        return new VaoState(id);
    }

    private static boolean matches(TerrainArenaDrawPlanner.Command command, Resident resident) {
        if (!matchesLogicalHandle(command, resident) || !TerrainPhysicalArenaPolicy.isDrawReady(command)) return false;
        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        return resident.vertexBytes == state.vertexPayloadBytes()
                && resident.indexBytes == state.indexPayloadBytes();
    }

    private static boolean matchesLogicalHandle(TerrainArenaDrawPlanner.Command command, Resident resident) {
        if (resident == null || command == null || command.handle() == null) return false;
        TerrainRegionAllocationRegistry.Handle handle = command.handle();
        return resident.arenaKey.equals(handle.arenaKey())
                && resident.handleGeneration == handle.generation();
    }

    private static boolean needsResize(Arena arena, int vertexCapacity, int indexCapacity) {
        if (arena.vertexBuffer.size < vertexCapacity) return true;
        return indexCapacity > 0 && (arena.indexBuffer == null || arena.indexBuffer.size < indexCapacity);
    }

    private static void ensureIndexBuffer(Arena arena, int capacity, boolean allowResize) {
        if (capacity <= 0) return;
        if (arena.indexBuffer == null) {
            arena.indexBuffer = new GpuBuffer(GlBufferTarget.INDICES, GlUsage.DYNAMIC_WRITE, capacity);
            return;
        }
        if (allowResize && arena.indexBuffer.size < capacity) arena.indexBuffer.resize(capacity);
    }

    private static void invalidate(VertexBuffer source) {
        Resident resident = RESIDENTS.remove(source);
        if (resident == null) return;
        invalidations++;
        detachResident(source, resident);
    }

    private static void invalidateArenaResidents(Arena arena) {
        VertexBuffer[] sources = arena.sources.keySet().toArray(VertexBuffer[]::new);
        for (VertexBuffer source : sources) {
            Resident removed = RESIDENTS.remove(source);
            if (removed != null) invalidations++;
        }
        arena.sources.clear();
    }

    private static void detachResident(VertexBuffer source, Resident resident) {
        Arena arena = ARENAS.get(resident.arenaKey);
        if (arena == null) return;
        arena.sources.remove(source);
        if (!arena.sources.isEmpty()) return;

        ARENAS.remove(resident.arenaKey);
        if (boundArena == arena) noteExternalBind();
        closeArena(arena);
    }

    private static void discardArenaIfUnused(TerrainRegionAllocationRegistry.ArenaKey key, Arena arena) {
        if (!arena.sources.isEmpty() || ARENAS.get(key) != arena) return;
        ARENAS.remove(key);
        if (boundArena == arena) noteExternalBind();
        closeArena(arena);
    }

    private static void closeArena(Arena arena) {
        for (VaoState vao : arena.vaos.values()) RenderSystem.glDeleteVertexArrays(vao.id);
        arena.vaos.clear();
        arena.vertexBuffer.close();
        if (arena.indexBuffer != null) arena.indexBuffer.close();
    }

    private static final class Arena {
        private final GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private final IdentityHashMap<VertexFormat, VaoState> vaos = new IdentityHashMap<>();
        private final IdentityHashMap<VertexBuffer, Boolean> sources = new IdentityHashMap<>();
        private long epoch = 1L;

        private Arena(int vertexCapacity, int indexCapacity) {
            this.vertexBuffer = new GpuBuffer(GlBufferTarget.VERTICES, GlUsage.DYNAMIC_WRITE, vertexCapacity);
            if (indexCapacity > 0) {
                this.indexBuffer = new GpuBuffer(GlBufferTarget.INDICES, GlUsage.DYNAMIC_WRITE, indexCapacity);
            }
        }
    }

    private static final class VaoState {
        private final int id;
        private boolean customIndexBound;
        private VertexFormat.DrawMode sequentialMode;

        private VaoState(int id) {
            this.id = id;
        }
    }

    private record Resident(
            TerrainRegionAllocationRegistry.ArenaKey arenaKey,
            long handleGeneration,
            long arenaEpoch,
            int vertexBytes,
            int indexBytes
    ) {
    }

    private record Prepared(
            VertexBuffer source,
            Resident resident,
            VertexFormat.IndexType indexType,
            long indexByteOffset
    ) {
    }

    public record Snapshot(
            long residentBytes,
            int activeArenas,
            int residentBuffers,
            long uploadedBytes,
            long physicalDraws,
            long customIndexDraws,
            long bufferBinds,
            long bindReuses,
            long arenaResizes,
            long invalidations
    ) {
    }
}
