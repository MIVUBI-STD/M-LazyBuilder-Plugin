package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;

import java.nio.ByteBuffer;

/**
 * Render-thread backend for shader programs that explicitly expose LazyBuilder per-draw transforms.
 *
 * Vanilla terrain shaders do not expose this contract, so prepare() fails open and normal ModelOffset
 * rendering remains authoritative. A compatible shader must provide the std140 uniform block named
 * {@value #TRANSFORM_BLOCK_NAME}, the integer uniform {@value #DRAW_BASE_UNIFORM}, and draw-id support.
 */
public final class TerrainPerDrawShaderBackend {
    public static final String TRANSFORM_BLOCK_NAME = "LazyBuilderDrawTransforms";
    public static final String DRAW_BASE_UNIFORM = "LazyBuilderDrawBase";
    private static final int TRANSFORM_BINDING_POINT = 7;
    private static final int CAPACITY_QUANTUM = 4096;

    private static TerrainPhysicalBuffer transformBuffer;
    private static volatile String status = "model-offset-uniform";
    private static volatile int activeProgramRef = -1;
    private static volatile int activeDrawBaseLocation = -1;
    private static volatile long prepareAttempts;
    private static volatile long successfulPrepares;
    private static volatile long uploadedBytes;
    private static volatile long bufferGrowths;

    private TerrainPerDrawShaderBackend() {
    }

    public static Probe probe(ShaderProgram program, int requiredBytes) {
        if (program == null) return new Probe(false, "missing-shader", -1, 0, -1);
        if (!RenderSystem.isOnRenderThread()) return new Probe(false, "wrong-thread", -1, 0, -1);

        var capabilities = GL.getCapabilities();
        if (!(capabilities.OpenGL46 || capabilities.GL_ARB_shader_draw_parameters)) {
            return new Probe(false, "draw-id-unsupported", -1, 0, -1);
        }

        int programRef = program.getGlRef();
        int blockIndex = GL31C.glGetUniformBlockIndex(programRef, TRANSFORM_BLOCK_NAME);
        if (blockIndex == -1) return new Probe(false, "transform-block-missing", -1, 0, -1);

        int blockBytes = GL31C.glGetActiveUniformBlocki(
                programRef,
                blockIndex,
                GL31C.GL_UNIFORM_BLOCK_DATA_SIZE
        );
        if (requiredBytes > 0 && blockBytes < requiredBytes) {
            return new Probe(false, "transform-block-too-small", blockIndex, blockBytes, -1);
        }

        int drawBaseLocation = GL20C.glGetUniformLocation(programRef, DRAW_BASE_UNIFORM);
        if (drawBaseLocation < 0) {
            return new Probe(false, "draw-base-uniform-missing", blockIndex, blockBytes, -1);
        }

        return new Probe(true, "ready", blockIndex, blockBytes, drawBaseLocation);
    }

    /** Upload and bind the current layer transform payload for a compatible first-party shader. */
    public static boolean prepare(ShaderProgram program, TerrainMultiDrawCommandStream.LayerPacket packet) {
        prepareAttempts++;
        activeProgramRef = -1;
        activeDrawBaseLocation = -1;

        if (packet == null || packet.commands().isEmpty()) {
            status = "empty-command-stream";
            return false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            status = "wrong-thread";
            return false;
        }

        ByteBuffer transforms = TerrainMultiDrawCommandStream.packTransforms(packet);
        Probe probe = probe(program, transforms.remaining());
        status = probe.status();
        if (!probe.ready()) return false;

        ensureCapacity(transforms.remaining());
        transformBuffer.upload(transforms, 0);
        GL31C.glUniformBlockBinding(program.getGlRef(), probe.blockIndex(), TRANSFORM_BINDING_POINT);
        transformBuffer.bindBase(TRANSFORM_BINDING_POINT);

        activeProgramRef = program.getGlRef();
        activeDrawBaseLocation = probe.drawBaseLocation();
        uploadedBytes += transforms.remaining();
        successfulPrepares++;
        status = "ready";
        return true;
    }

    /** Set the packet-relative first transform used by gl_DrawID for one multi-draw run. */
    public static boolean bindDrawBase(ShaderProgram program, int transformBase) {
        if (!RenderSystem.isOnRenderThread() || program == null || transformBase < 0) return false;
        if (program.getGlRef() != activeProgramRef || activeDrawBaseLocation < 0 || !"ready".equals(status)) {
            return false;
        }
        GL20C.glUniform1i(activeDrawBaseLocation, transformBase);
        return true;
    }

    public static boolean preparedFor(ShaderProgram program) {
        return program != null
                && "ready".equals(status)
                && activeProgramRef == program.getGlRef()
                && activeDrawBaseLocation >= 0;
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                status,
                transformBuffer == null ? 0 : transformBuffer.size(),
                prepareAttempts,
                successfulPrepares,
                uploadedBytes,
                bufferGrowths
        );
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(TerrainPerDrawShaderBackend::clear);
            return;
        }
        if (transformBuffer != null) {
            transformBuffer.close();
            transformBuffer = null;
        }
        status = "model-offset-uniform";
        activeProgramRef = -1;
        activeDrawBaseLocation = -1;
        prepareAttempts = 0L;
        successfulPrepares = 0L;
        uploadedBytes = 0L;
        bufferGrowths = 0L;
    }

    static int plannedCapacity(int requiredBytes) {
        if (requiredBytes <= 0) return 0;
        long aligned = ((long) requiredBytes + CAPACITY_QUANTUM - 1L) / CAPACITY_QUANTUM * CAPACITY_QUANTUM;
        return aligned > Integer.MAX_VALUE ? -1 : (int) aligned;
    }

    private static void ensureCapacity(int requiredBytes) {
        int planned = plannedCapacity(requiredBytes);
        if (planned < 0) throw new IllegalArgumentException("Terrain transform buffer exceeds supported size");
        if (transformBuffer == null) {
            transformBuffer = new TerrainPhysicalBuffer(TerrainPhysicalBuffer.UNIFORM, planned);
            bufferGrowths++;
            return;
        }
        if (transformBuffer.size() >= planned) return;
        transformBuffer.resizeDiscarding(planned);
        bufferGrowths++;
    }

    public record Probe(boolean ready, String status, int blockIndex, int blockBytes, int drawBaseLocation) {
    }

    public record Snapshot(
            String status,
            int bufferCapacityBytes,
            long prepareAttempts,
            long successfulPrepares,
            long uploadedBytes,
            long bufferGrowths
    ) {
    }
}
