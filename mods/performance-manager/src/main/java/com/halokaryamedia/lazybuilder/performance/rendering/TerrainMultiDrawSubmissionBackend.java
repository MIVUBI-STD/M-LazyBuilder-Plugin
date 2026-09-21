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
            VertexBuffer first = run.commandAt(0).source();
            STARTS.put(first, run);
            for (int index = 0; index < run.commandCount(); index++) {
                TerrainMultiDrawCommandStream.PackedCommand command = run.commandAt(index);
                MEMBERS.put(command.source(), run);
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
            TerrainMultiDrawCommandStream.PackedCommand command = startRun.commandAt(index);
            if (command.source() == null || !TerrainPhysicalArenaManager.bind(command.source())) {
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
        if (run == null || run.failed || run.commandAt(0).source() != source) return DrawAction.NONE;

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
        if (packet == null || packet.commands().isEmpty()) return List.of();

        List<TerrainMultiDrawCommandStream.PackedCommand> commands = packet.commands();
        List<Run> runs = new ArrayList<>();
        int runStart = -1;
        TerrainMultiDrawCommandStream.PackedCommand previous = null;

        for (int index = 0; index < commands.size(); index++) {
            TerrainMultiDrawCommandStream.PackedCommand command = commands.get(index);
            if (command == null) {
                flushRun(runs, commands, runStart, index);
                runStart = -1;
                previous = null;
                continue;
            }

            if (previous == null || !sameRun(previous, command)) {
                flushRun(runs, commands, runStart, index);
                runStart = index;
            }
            previous = command;
        }
        flushRun(runs, commands, runStart, commands.size());
        return List.copyOf(runs);
    }

    private static boolean submit(Run run) {
        if (run == null || activeProgram == null || run.commandCount() < 2) return false;
        TerrainMultiDrawCommandStream.PackedCommand first = run.commandAt(0);
        TerrainArenaDrawStateRegistry.DrawState state = first.arenaCommand().state();
        if (state == null || !TerrainPerDrawShaderBackend.beginMultiDraw(activeProgram, first.transformIndex())) {
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
                TerrainMultiDrawCommandStream.PackedCommand command = run.commandAt(commandIndex);
                counts.put(command.indexCount());
                offsets.put(customIndices ? command.indexByteOffset() : 0L);
                baseVertices.put(command.baseVertex());
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
                    run.commands,
                    run.start,
                    run.end
            );
            return true;
        } catch (RuntimeException ex) {
            TerrainPhysicalArenaManager.recordMultiDrawFailure(
                    run.commands,
                    run.start,
                    run.end
            );
            return false;
        } finally {
            TerrainPerDrawShaderBackend.endMultiDraw(activeProgram);
        }
    }

    private static boolean sameRun(
            TerrainMultiDrawCommandStream.PackedCommand left,
            TerrainMultiDrawCommandStream.PackedCommand right
    ) {
        if (left.orderIndex() + 1 != right.orderIndex()) return false;
        TerrainArenaDrawPlanner.Command leftArena = left.arenaCommand();
        TerrainArenaDrawPlanner.Command rightArena = right.arenaCommand();
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
            List<Run> runs,
            List<TerrainMultiDrawCommandStream.PackedCommand> commands,
            int start,
            int end
    ) {
        if (commands != null && start >= 0 && end - start > 1) {
            runs.add(new Run(commands, start, end));
        }
    }

    private static boolean runtimeSourcesPresent(Run run) {
        for (int index = 0; index < run.commandCount(); index++) {
            if (run.commandAt(index).source() == null) return false;
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
        private final List<TerrainMultiDrawCommandStream.PackedCommand> commands;
        private final int start;
        private final int end;
        private boolean submitted;
        private boolean failed;

        private Run(
                List<TerrainMultiDrawCommandStream.PackedCommand> commands,
                int start,
                int end
        ) {
            this.commands = commands;
            this.start = start;
            this.end = end;
        }

        TerrainMultiDrawCommandStream.PackedCommand commandAt(int index) {
            if (index < 0 || index >= commandCount()) {
                throw new IndexOutOfBoundsException(index);
            }
            return commands.get(start + index);
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
