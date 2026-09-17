package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private static long relocations;
    private static long relocatedBytes;
    private static long relocationFallbacks;

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
        } else if (layoutChanged(arena) || needsResize(arena, vertexCapacity, indexCapacity)) {
            int targetVertexCapacity = Math.max(arena.vertexBuffer.size(), vertexCapacity);
            int targetIndexCapacity = Math.max(arena.indexBuffer == null ? 0 : arena.indexBuffer.size(), indexCapacity);
            relocateArena(arena, targetVertexCapacity, targetIndexCapacity);
            arena = ARENAS.get(key);
            if (arena == null) {
                arena = new Arena(vertexCapacity, indexCapacity);
                ARENAS.put(key, arena);
            }
        } else if (indexCapacity > 0) {
            ensureIndexBuffer(arena, indexCapacity);
        }

        int vertexOffset = checkedOffset(command.vertexByteOffset());
        int indexOffset = indexBytes > 0 ? checkedOffset(command.indexByteOffset()) : 0;
        if (vertexOffset < 0 || (indexBytes > 0 && indexOffset < 0)) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        Resident previous = RESIDENTS.get(source);
        if (previous != null && !previous.arenaKey.equals(key)) detachResident(source, previous);

        try {
            arena.vertexBuffer.upload(vertices.duplicate(), vertexOffset);
            uploadedBytes += vertexBytes;

            if (indexBytes > 0) {
                if (arena.indexBuffer == null) {
                    invalidate(source);
                    discardArenaIfUnused(key, arena);
                    return false;
                }
                arena.indexBuffer.upload(customIndices.duplicate(), indexOffset);
                uploadedBytes += indexBytes;
            }
        } catch (RuntimeException ex) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        arena.sources.put(source, Boolean.TRUE);
        RESIDENTS.put(source, new Resident(
                key,
                handle.generation(),
                arena.epoch,
                vertexOffset,
                indexOffset,
                vertexBytes,
                indexBytes
        ));
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
            Arena arena = resident == null ? null : ARENAS.get(resident.arenaKey);
            if (arena != null) relocateArena(arena, arena.vertexBuffer.size(), arena.indexBuffer == null ? 0 : arena.indexBuffer.size());
            resident = RESIDENTS.get(source);
            if (!matchesLogicalHandle(command, resident)) {
                invalidate(source);
                return false;
            }
        }

        Arena arena = ARENAS.get(resident.arenaKey);
        int requiredCapacity = TerrainPhysicalArenaPolicy.plannedIndexCapacity(command, indexBytes);
        if (arena == null || requiredCapacity < 0) {
            invalidate(source);
            return false;
        }
        if (arena.indexBuffer == null || arena.indexBuffer.size() < requiredCapacity) {
            relocateArena(arena, arena.vertexBuffer.size(), requiredCapacity);
            arena = ARENAS.get(resident.arenaKey);
            resident = RESIDENTS.get(source);
        }
        if (arena == null || arena.indexBuffer == null || resident == null || !matchesLogicalHandle(command, resident)) {
            invalidate(source);
            return false;
        }

        int indexOffset = checkedOffset(command.indexByteOffset());
        if (indexOffset < 0) {
            invalidate(source);
            return false;
        }

        try {
            arena.indexBuffer.upload(indices.duplicate(), indexOffset);
        } catch (RuntimeException ex) {
            invalidate(source);
            return false;
        }

        RESIDENTS.put(source, new Resident(
                resident.arenaKey,
                command.handle().generation(),
                arena.epoch,
                resident.vertexOffset,
                indexOffset,
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
            Arena staleArena = resident == null ? null : ARENAS.get(resident.arenaKey);
            if (staleArena != null && command != null && command.handle() != null
                    && command.handle().arenaKey().equals(resident.arenaKey)) {
                relocateArena(staleArena, staleArena.vertexBuffer.size(), staleArena.indexBuffer == null ? 0 : staleArena.indexBuffer.size());
                resident = RESIDENTS.get(source);
            }
            if (!matches(command, resident)) {
                invalidate(source);
                return false;
            }
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
        relocations = 0L;
        relocatedBytes = 0L;
        relocationFallbacks = 0L;
    }

    public static Snapshot snapshot() {
        long bytes = 0L;
        for (Arena arena : ARENAS.values()) {
            bytes += Math.max(0, arena.vertexBuffer.size());
            if (arena.indexBuffer != null) bytes += Math.max(0, arena.indexBuffer.size());
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
                invalidations,
                relocations,
                relocatedBytes,
                relocationFallbacks
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

    private static boolean layoutChanged(Arena arena) {
        for (VertexBuffer source : arena.sources.keySet()) {
            Resident resident = RESIDENTS.get(source);
            TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
            if (resident == null || command == null || command.handle() == null
                    || !resident.arenaKey.equals(command.handle().arenaKey())
                    || resident.handleGeneration != command.handle().generation()) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsResize(Arena arena, int vertexCapacity, int indexCapacity) {
        if (arena.vertexBuffer.size() < vertexCapacity) return true;
        return indexCapacity > 0 && (arena.indexBuffer == null || arena.indexBuffer.size() < indexCapacity);
    }

    private static void ensureIndexBuffer(Arena arena, int capacity) {
        if (capacity <= 0 || arena.indexBuffer != null) return;
        arena.indexBuffer = new TerrainPhysicalBuffer(TerrainPhysicalBuffer.INDICES, capacity);
        invalidateVaos(arena);
    }

    /** Rebuild a physical arena, preserving mirrored data whose current logical handles are valid. */
    private static void relocateArena(Arena arena, int requestedVertexCapacity, int requestedIndexCapacity) {
        if (arena == null || !RenderSystem.isOnRenderThread()) return;

        TerrainPhysicalBuffer oldVertex = arena.vertexBuffer;
        TerrainPhysicalBuffer oldIndex = arena.indexBuffer;
        int vertexCapacity = Math.max(oldVertex.size(), requestedVertexCapacity);
        int indexCapacity = Math.max(oldIndex == null ? 0 : oldIndex.size(), requestedIndexCapacity);

        for (VertexBuffer source : arena.sources.keySet()) {
            TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
            Resident resident = RESIDENTS.get(source);
            if (command == null || command.handle() == null || resident == null
                    || !command.handle().arenaKey().equals(resident.arenaKey)) continue;
            int vertexEnd = safeEnd(command.vertexByteOffset(), resident.vertexBytes);
            if (vertexEnd > vertexCapacity) vertexCapacity = plannedCapacity(vertexEnd);
            if (resident.indexBytes > 0) {
                int indexEnd = safeEnd(command.indexByteOffset(), resident.indexBytes);
                if (indexEnd > indexCapacity) indexCapacity = plannedCapacity(indexEnd);
            }
        }

        if (vertexCapacity <= 0 || indexCapacity < 0) {
            invalidateArenaResidents(arena);
            relocationFallbacks++;
            return;
        }

        TerrainPhysicalBuffer newVertex = new TerrainPhysicalBuffer(TerrainPhysicalBuffer.VERTICES, vertexCapacity);
        TerrainPhysicalBuffer newIndex = indexCapacity > 0
                ? new TerrainPhysicalBuffer(TerrainPhysicalBuffer.INDICES, indexCapacity)
                : null;

        long nextEpoch = arena.epoch + 1L;
        IdentityHashMap<VertexBuffer, Boolean> preserved = new IdentityHashMap<>();
        try {
            VertexBuffer[] sources = arena.sources.keySet().toArray(VertexBuffer[]::new);
            for (VertexBuffer source : sources) {
                Resident resident = RESIDENTS.get(source);
                TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
                if (resident == null || command == null || command.handle() == null
                        || !command.handle().arenaKey().equals(resident.arenaKey)
                        || !TerrainPhysicalArenaPolicy.isDrawReady(command)) {
                    if (resident != null) {
                        RESIDENTS.remove(source);
                        invalidations++;
                    }
                    continue;
                }

                int newVertexOffset = checkedOffset(command.vertexByteOffset());
                int newIndexOffset = resident.indexBytes > 0 ? checkedOffset(command.indexByteOffset()) : 0;
                if (newVertexOffset < 0 || (resident.indexBytes > 0 && (newIndexOffset < 0 || newIndex == null))) {
                    RESIDENTS.remove(source);
                    invalidations++;
                    continue;
                }

                try {
                    oldVertex.copyTo(newVertex, resident.vertexOffset, newVertexOffset, resident.vertexBytes);
                    long copied = resident.vertexBytes;
                    if (resident.indexBytes > 0) {
                        if (oldIndex == null) throw new IllegalStateException("Missing old terrain EBO");
                        oldIndex.copyTo(newIndex, resident.indexOffset, newIndexOffset, resident.indexBytes);
                        copied += resident.indexBytes;
                    }
                    relocatedBytes += copied;
                    relocations++;
                    preserved.put(source, Boolean.TRUE);
                    RESIDENTS.put(source, new Resident(
                            resident.arenaKey,
                            command.handle().generation(),
                            nextEpoch,
                            newVertexOffset,
                            newIndexOffset,
                            resident.vertexBytes,
                            resident.indexBytes
                    ));
                } catch (RuntimeException ex) {
                    RESIDENTS.remove(source);
                    invalidations++;
                    relocationFallbacks++;
                }
            }
        } finally {
            noteExternalBind();
            invalidateVaos(arena);
            oldVertex.close();
            if (oldIndex != null) oldIndex.close();
        }

        arena.vertexBuffer = newVertex;
        arena.indexBuffer = newIndex;
        arena.sources.clear();
        arena.sources.putAll(preserved);
        arena.epoch = nextEpoch;
        arenaResizes++;
    }

    private static int safeEnd(long offset, int bytes) {
        if (offset < 0L || offset > Integer.MAX_VALUE || bytes < 0 || offset > Integer.MAX_VALUE - (long) bytes) return -1;
        return (int) offset + bytes;
    }

    private static int checkedOffset(long offset) {
        return offset < 0L || offset > Integer.MAX_VALUE ? -1 : (int) offset;
    }

    private static int plannedCapacity(int requiredEnd) {
        if (requiredEnd <= 0) return -1;
        long planned = TerrainRegionArenaPolicy.plannedCapacity(requiredEnd);
        return planned < requiredEnd || planned > Integer.MAX_VALUE ? -1 : (int) planned;
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

    private static void invalidateVaos(Arena arena) {
        for (VaoState vao : arena.vaos.values()) RenderSystem.glDeleteVertexArrays(vao.id);
        arena.vaos.clear();
    }

    private static void closeArena(Arena arena) {
        invalidateVaos(arena);
        arena.vertexBuffer.close();
        if (arena.indexBuffer != null) arena.indexBuffer.close();
    }

    private static final class Arena {
        private TerrainPhysicalBuffer vertexBuffer;
        private TerrainPhysicalBuffer indexBuffer;
        private final IdentityHashMap<VertexFormat, VaoState> vaos = new IdentityHashMap<>();
        private final IdentityHashMap<VertexBuffer, Boolean> sources = new IdentityHashMap<>();
        private long epoch = 1L;

        private Arena(int vertexCapacity, int indexCapacity) {
            this.vertexBuffer = new TerrainPhysicalBuffer(TerrainPhysicalBuffer.VERTICES, vertexCapacity);
            if (indexCapacity > 0) {
                this.indexBuffer = new TerrainPhysicalBuffer(TerrainPhysicalBuffer.INDICES, indexCapacity);
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
            int vertexOffset,
            int indexOffset,
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
            long invalidations,
            long relocations,
            long relocatedBytes,
            long relocationFallbacks
    ) {
    }
}
