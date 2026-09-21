package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Guarded true multi-draw submission for shaders satisfying the LazyBuilder transform contract. */
public final class TerrainMultiDrawSubmissionBackend {
    private static final IdentityHashMap<VertexBuffer, Run> STARTS = new IdentityHashMap<>();
    private static final IdentityHashMap<VertexBuffer, Run> MEMBERS = new IdentityHashMap<>();
    private static final ArrayList<Run> RUN_POOL = new ArrayList<>();
    private static final ArrayList<Run> PLANNED_RUNS = new ArrayList<>();
    private static int runPoolCursor;

    private static ShaderProgram activeProgram;
    private static Run pendingRun;
    private static volatile boolean sessionDisabled;
    private static volatile String status = "inactive";
    private static volatile long prepareAttempts;
    private static volatile long preparedRuns;
    private static volatile long submittedBatches;
    private static volatile long submittedCommands;
    private static volatile long reducedDrawCalls;
    private static volatile long submissionFailures;

    private TerrainMultiDrawSubmissionBackend() {
    }

    public static boolean prepare(ShaderProgram program, TerrainMultiDrawCommandStream.LayerPacket packet) {
        prepareAttempts++;
        clearSession();

        if (sessionDisabled) {
            status = "session-disabled-after-failure";
            return false;
        }

        TerrainMultiDrawCapability.Snapshot capability = TerrainMultiDrawCapability.current(program);
        if (!capability.ready()) {
            status = capability.status();
            return false;
        }
        if (!TerrainPerDrawShaderBackend.prepare(program, packet)) {
            status = TerrainPerDrawShaderBackend.snapshot().status();
            return false;
        }

        List<Run> runs = planRuns(packet);
        for (Run run : runs) {
            if (run.commandCount() < 2 || !runtimeSourcesPresent(run)) continue;
            VertexBuffer first = run.sourceAt(0);
            STARTS.put(first, run);
            for (int index = 0; index < run.commandCount(); index++) {
                MEMBERS.put(run.sourceAt(index), run);
            }
        }

        if (STARTS.isEmpty()) {
            status = "no-multi-draw-runs";
            return false;
        }

        activeProgram = program;
        preparedRuns += STARTS.size();
        status = "ready";
        return true;
    }

    public static BindAction onBind(VertexBuffer source) {
        if (source == null || activeProgram == null || !RenderSystem.isOnRenderThread()) return BindAction.NONE;
        Run memberRun = MEMBERS.get(source);
        if (memberRun == null) return BindAction.NONE;
        if (memberRun.submitted) return BindAction.SKIP;

        Run startRun = STARTS.get(source);
        if (startRun == null || startRun.failed) return BindAction.NONE;

        for (int index = 0; index < startRun.commandCount(); index++) {
            VertexBuffer source = startRun.sourceAt(index);
            if (source == null || !TerrainPhysicalArenaManager.bind(source)) {
                startRun.failed = true;
                status = "physical-residency-fallback";
                return BindAction.NONE;
            }
        }
        if (!TerrainPhysicalArenaManager.bind(source)) {
            startRun.failed = true;
            status = "physical-residency-fallback";
            return BindAction.NONE;
        }

        pendingRun = startRun;
        return BindAction.START;
    }

    public static DrawAction onDraw(VertexBuffer source) {
        if (source == null || activeProgram == null || !RenderSystem.isOnRenderThread()) return DrawAction.NONE;
        Run memberRun = MEMBERS.get(source);
        if (memberRun == null) return DrawAction.NONE;
        if (memberRun.submitted) return DrawAction.SKIP;

        Run run = pendingRun;
        pendingRun = null;
        if (run == null || run.failed || run.sourceAt(0) != source) return DrawAction.NONE;

        if (!submit(run)) {
            run.failed = true;
            submissionFailures++;
            sessionDisabled = true;
            status = "session-disabled-after-failure";
            clearSession();
            return DrawAction.FALLBACK;
        }

        run.submitted = true;
        submittedBatches++;
        submittedCommands += run.commandCount();
        reducedDrawCalls += run.commandCount() - 1L;
        status = "active";
        return DrawAction.SUBMITTED;
    }

    static List<Run> planRuns(TerrainMultiDrawCommandStream.LayerPacket packet) {
        PLANNED_RUNS.clear();
        runPoolCursor = 0;
        if (packet == null || packet.commandCount() == 0) return PLANNED_RUNS;

        int runStart = -1;
        for (int index = 0; index < packet.commandCount(); index++) {
            if (runStart < 0) {
                runStart = index;
                continue;
            }

            if (!sameRun(packet, index - 1, index)) {
                flushRun(packet, runStart, index);
                runStart = index;
            }
        }

        flushRun(packet, runStart, packet.commandCount());
        return PLANNED_RUNS;
    }

    private static boolean submit(Run run) {
        if (run == null || activeProgram == null || run.commandCount() < 2) return false;
        TerrainArenaDrawPlanner.Command firstArena = run.arenaCommandAt(0);
        TerrainArenaDrawStateRegistry.DrawState state =
                firstArena == null ? null : firstArena.state();
        if (state == null
                || !TerrainPerDrawShaderBackend.beginMultiDraw(
                activeProgram,
                run.transformIndexAt(0)
        )) {
            return false;
        }

        VertexFormat.IndexType indexType;
        boolean customIndices = state.indexPayloadBytes() > 0;
        if (customIndices) {
            indexType = state.indexType();
        } else {
            indexType = RenderSystem.getSequentialBuffer(state.mode()).getIndexType();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int count = run.commandCount();
            IntBuffer counts = stack.mallocInt(count);
            PointerBuffer offsets = stack.mallocPointer(count);
            IntBuffer baseVertices = stack.mallocInt(count);

            for (int commandIndex = 0; commandIndex < run.commandCount(); commandIndex++) {
                counts.put(run.indexCountAt(commandIndex));
                offsets.put(customIndices ? run.indexByteOffsetAt(commandIndex) : 0L);
                baseVertices.put(run.baseVertexAt(commandIndex));
            }
            counts.flip();
            offsets.flip();
            baseVertices.flip();

            GL32C.glMultiDrawElementsBaseVertex(
                    state.mode().glMode,
                    counts,
                    indexType.glType,
                    offsets,
                    baseVertices
            );
            TerrainPhysicalArenaManager.recordMultiDrawSuccess(
                    run.packet,
                    run.start,
                    run.end
            );
            return true;
        } catch (RuntimeException ex) {
            TerrainPhysicalArenaManager.recordMultiDrawFailure(
                    run.packet,
                    run.start,
                    run.end
            );
            return false;
        } finally {
            TerrainPerDrawShaderBackend.endMultiDraw(activeProgram);
        }
    }

    private static boolean sameRun(
            TerrainMultiDrawCommandStream.LayerPacket packet,
            int leftIndex,
            int rightIndex
    ) {
        if (packet.orderIndex(leftIndex) + 1 != packet.orderIndex(rightIndex)) return false;

        TerrainArenaDrawPlanner.Command leftArena = packet.arenaCommand(leftIndex);
        TerrainArenaDrawPlanner.Command rightArena = packet.arenaCommand(rightIndex);
        if (leftArena == null || rightArena == null
                || leftArena.handle() == null || rightArena.handle() == null
                || leftArena.state() == null || rightArena.state() == null) {
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState leftState = leftArena.state();
        TerrainArenaDrawStateRegistry.DrawState rightState = rightArena.state();
        return leftArena.handle().arenaKey().equals(rightArena.handle().arenaKey())
                && leftState.format() == rightState.format()
                && leftState.mode() == rightState.mode()
                && leftState.indexType() == rightState.indexType()
                && (leftState.indexPayloadBytes() > 0) == (rightState.indexPayloadBytes() > 0);
    }

    private static void flushRun(
            TerrainMultiDrawCommandStream.LayerPacket packet,
            int start,
            int end
    ) {
        if (packet == null || start < 0 || end - start <= 1) return;

        Run run;
        if (runPoolCursor < RUN_POOL.size()) {
            run = RUN_POOL.get(runPoolCursor);
        } else {
            run = new Run();
            RUN_POOL.add(run);
        }
        runPoolCursor++;
        run.reset(packet, start, end);
        PLANNED_RUNS.add(run);
    }

    private static boolean runtimeSourcesPresent(Run run) {
        for (int index = 0; index < run.commandCount(); index++) {
            if (run.sourceAt(index) == null) return false;
        }
        return true;
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                status,
                prepareAttempts,
                preparedRuns,
                submittedBatches,
                submittedCommands,
                reducedDrawCalls,
                submissionFailures,
                sessionDisabled
        );
    }

    public static void finishLayer() {
        if (activeProgram != null) TerrainPerDrawShaderBackend.endMultiDraw(activeProgram);
        clearSession();
        if (!sessionDisabled && ("active".equals(status) || "ready".equals(status))) status = "inactive";
    }

    public static void clear() {
        if (activeProgram != null) TerrainPerDrawShaderBackend.endMultiDraw(activeProgram);
        clearSession();
        sessionDisabled = false;
        status = "inactive";
        prepareAttempts = 0L;
        preparedRuns = 0L;
        submittedBatches = 0L;
        submittedCommands = 0L;
        reducedDrawCalls = 0L;
        submissionFailures = 0L;
    }

    /**
     * Resource reload invalidation must not touch the previous shader program: it may already be
     * detached or scheduled for deletion by Minecraft's shader reload lifecycle.
     */
    public static void invalidateForShaderReload() {
        clearSession();
        sessionDisabled = false;
        status = "shader-reload";
    }

    private static void clearSession() {
        STARTS.clear();
        MEMBERS.clear();
        activeProgram = null;
        pendingRun = null;
    }

    public enum BindAction {
        NONE,
        START,
        SKIP
    }

    public enum DrawAction {
        NONE,
        SUBMITTED,
        SKIP,
        FALLBACK
    }

    static final class Run {
        private TerrainMultiDrawCommandStream.LayerPacket packet;
        private int start;
        private int end;
        private boolean submitted;
        private boolean failed;

        private Run() {
        }

        private void reset(
                TerrainMultiDrawCommandStream.LayerPacket packet,
                int start,
                int end
        ) {
            this.packet = packet;
            this.start = start;
            this.end = end;
            this.submitted = false;
            this.failed = false;
        }

        VertexBuffer sourceAt(int index) {
            return packet.source(start + index);
        }

        TerrainArenaDrawPlanner.Command arenaCommandAt(int index) {
            return packet.arenaCommand(start + index);
        }

        int indexCountAt(int index) {
            return packet.indexCount(start + index);
        }

        long indexByteOffsetAt(int index) {
            return packet.indexByteOffset(start + index);
        }

        int baseVertexAt(int index) {
            return packet.baseVertex(start + index);
        }

        int transformIndexAt(int index) {
            return packet.transformIndex(start + index);
        }

        public int commandCount() {
            return end - start;
        }
    }

    public record Snapshot(
            String status,
            long prepareAttempts,
            long preparedRuns,
            long submittedBatches,
            long submittedCommands,
            long reducedDrawCalls,
            long submissionFailures,
            boolean sessionDisabled
    ) {
    }
}
