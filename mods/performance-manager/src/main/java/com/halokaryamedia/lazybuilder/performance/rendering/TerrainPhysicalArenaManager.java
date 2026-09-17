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

/**
 * Render-thread physical backing for the first safe shared-VBO terrain subset.
 *
 * Vanilla per-section VertexBuffers are still populated as the correctness fallback. This manager
 * mirrors only sequential-index terrain vertices into region/layer GpuBuffers, validates logical
 * allocation generations before use, and draws through Minecraft's shared sequential index buffer
 * with glDrawElementsBaseVertex. If an arena grows or a logical handle moves, affected mirrored
 * residents are invalidated and the vanilla path remains authoritative until they upload again.
 */
public final class TerrainPhysicalArenaManager {
    private static final Map<TerrainRegionAllocationRegistry.ArenaKey, Arena> ARENAS = new HashMap<>();
    private static final IdentityHashMap<VertexBuffer, Resident> RESIDENTS = new IdentityHashMap<>();

    private static Arena boundArena;
    private static int boundVao = -1;
    private static Prepared prepared;

    private static long uploadedBytes;
    private static long physicalDraws;
    private static long physicalBufferBinds;
    private static long physicalBindReuses;
    private static long arenaResizes;
    private static long invalidations;

    private TerrainPhysicalArenaManager() {
    }

    public static boolean uploadVertex(VertexBuffer source, ByteBuffer vertices) {
        if (source == null || vertices == null || !RenderSystem.isOnRenderThread()) return false;

        TerrainArenaDrawPlanner.Command command = TerrainGpuResidencyTracker.drawCommand(source);
        int bytes = vertices.remaining();
        int plannedCapacity = TerrainPhysicalArenaPolicy.plannedCapacity(command, bytes);
        if (plannedCapacity < 0) {
            invalidate(source);
            return false;
        }

        TerrainRegionAllocationRegistry.Handle handle = command.handle();
        TerrainRegionAllocationRegistry.ArenaKey key = handle.arenaKey();
        Arena arena = ARENAS.get(key);
        if (arena == null) {
            arena = new Arena(plannedCapacity);
            ARENAS.put(key, arena);
        } else if (arena.vertexBuffer.size < plannedCapacity) {
            invalidateArenaResidents(arena);
            arena.vertexBuffer.resize(plannedCapacity);
            arena.epoch++;
            arenaResizes++;
            noteExternalBind();
        }

        long offset = command.vertexByteOffset();
        if (offset < 0L || offset > Integer.MAX_VALUE) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        Resident previous = RESIDENTS.get(source);
        if (previous != null && !previous.arenaKey.equals(key)) {
            detachResident(source, previous);
        }

        try {
            arena.vertexBuffer.copyFrom(vertices.duplicate(), (int) offset);
        } catch (RuntimeException ex) {
            invalidate(source);
            discardArenaIfUnused(key, arena);
            return false;
        }

        arena.sources.put(source, Boolean.TRUE);
        RESIDENTS.put(source, new Resident(key, handle.generation(), arena.epoch, bytes));
        uploadedBytes += bytes;
        prepared = null;
        return true;
    }

    /** Bind a physical region VAO when the mirrored bytes still match the current logical handle. */
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

        RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(state.mode());
        if (vao.indexMode != state.mode() || !sequential.isLargeEnough(state.indexCount())) {
            sequential.bindAndGrow(state.indexCount());
            vao.indexMode = state.mode();
        }

        prepared = new Prepared(source, resident, sequential.getIndexType());
        return true;
    }

    /** Draw the command prepared by {@link #bind(VertexBuffer)} using the shared sequential EBO. */
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

        int baseVertex = TerrainArenaBaseVertexPolicy.baseVertex(command);
        if (baseVertex < 0) {
            prepared = null;
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        GL32C.glDrawElementsBaseVertex(
                state.mode().glMode,
                state.indexCount(),
                current.indexType.glType,
                0L,
                baseVertex
        );
        physicalDraws++;
        prepared = null;
        return true;
    }

    /** Mark VAO state as externally changed before falling back to a vanilla VertexBuffer bind. */
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
        if (resident != null) {
            detachResident(source, resident);
        }
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(TerrainPhysicalArenaManager::clear);
            return;
        }

        noteExternalBind();
        for (Arena arena : ARENAS.values()) {
            closeArena(arena);
        }
        ARENAS.clear();
        RESIDENTS.clear();
        uploadedBytes = 0L;
        physicalDraws = 0L;
        physicalBufferBinds = 0L;
        physicalBindReuses = 0L;
        arenaResizes = 0L;
        invalidations = 0L;
    }

    public static Snapshot snapshot() {
        long bytes = 0L;
        for (Arena arena : ARENAS.values()) bytes += Math.max(0, arena.vertexBuffer.size);
        return new Snapshot(
                bytes,
                ARENAS.size(),
                RESIDENTS.size(),
                uploadedBytes,
                physicalDraws,
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
        if (resident == null || !TerrainArenaBaseVertexPolicy.isReady(command)) return false;
        TerrainRegionAllocationRegistry.Handle handle = command.handle();
        return handle != null
                && resident.arenaKey.equals(handle.arenaKey())
                && resident.handleGeneration == handle.generation()
                && resident.vertexBytes == command.state().vertexPayloadBytes();
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
        for (VaoState vao : arena.vaos.values()) {
            RenderSystem.glDeleteVertexArrays(vao.id);
        }
        arena.vaos.clear();
        arena.vertexBuffer.close();
    }

    private static final class Arena {
        private final GpuBuffer vertexBuffer;
        private final IdentityHashMap<VertexFormat, VaoState> vaos = new IdentityHashMap<>();
        private final IdentityHashMap<VertexBuffer, Boolean> sources = new IdentityHashMap<>();
        private long epoch = 1L;

        private Arena(int capacity) {
            this.vertexBuffer = new GpuBuffer(GlBufferTarget.VERTICES, GlUsage.DYNAMIC_WRITE, capacity);
        }
    }

    private static final class VaoState {
        private final int id;
        private VertexFormat.DrawMode indexMode;

        private VaoState(int id) {
            this.id = id;
        }
    }

    private record Resident(
            TerrainRegionAllocationRegistry.ArenaKey arenaKey,
            long handleGeneration,
            long arenaEpoch,
            int vertexBytes
    ) {
    }

    private record Prepared(
            VertexBuffer source,
            Resident resident,
            VertexFormat.IndexType indexType
    ) {
    }

    public record Snapshot(
            long residentBytes,
            int activeArenas,
            int residentBuffers,
            long uploadedBytes,
            long physicalDraws,
            long bufferBinds,
            long bindReuses,
            long arenaResizes,
            long invalidations
    ) {
    }
}
