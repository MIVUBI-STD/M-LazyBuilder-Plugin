package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL31C;

import java.nio.ByteBuffer;

/**
 * Render-thread backend for shader programs that explicitly expose LazyBuilder per-draw transforms.
 *
 * Vanilla terrain shaders do not expose this block, so prepare() fails open and normal ModelOffset
 * uniform rendering remains authoritative. A compatible shader must provide the std140 uniform block
 * named {@value #TRANSFORM_BLOCK_NAME} and draw-id support.
 */
public final class TerrainPerDrawShaderBackend {
    public static final String TRANSFORM_BLOCK_NAME = "LazyBuilderDrawTransforms";
    private static final int TRANSFORM_BINDING_POINT = 7;
    private static final int CAPACITY_QUANTUM = 4096;

    private static TerrainPhysicalBuffer transformBuffer;
    private static volatile String status = "model-offset-uniform";
    private static volatile long prepareAttempts;
    private static volatile long successfulPrepares;
    private static volatile long uploadedBytes;
    private static volatile long bufferGrowths;

    private TerrainPerDrawShaderBackend() {
    }

    public static Probe probe(ShaderProgram program, int requiredBytes) {
        if (program == null) return new Probe(false, "missing-shader", -1, 0);
        if (!RenderSystem.isOnRenderThread()) return new Probe(false, "wrong-thread", -1, 0);

        var capabilities = GL.getCapabilities();
        if (!(capabilities.OpenGL46 || capabilities.GL_ARB_shader_draw_parameters)) {
            return new Probe(false, "draw-id-unsupported", -1, 0);
        }

        int blockIndex = GL31C.glGetUniformBlockIndex(program.getGlRef(), TRANSFORM_BLOCK_NAME);
        if (blockIndex == -1) return new Probe(false, "transform-block-missing", -1, 0);

        int blockBytes = GL31C.glGetActiveUniformBlocki(
                program.getGlRef(),
                blockIndex,
                GL31C.GL_UNIFORM_BLOCK_DATA_SIZE
        );
        if (requiredBytes > 0 && blockBytes < requiredBytes) {
            return new Probe(false, "transform-block-too-small", blockIndex, blockBytes);
        }

        return new Probe(true, "ready", blockIndex, blockBytes);
    }

    /**
     * Upload and bind the current layer transform payload when the active shader explicitly supports
     * LazyBuilder per-draw transforms. Returns false without mutating shader semantics otherwise.
     */
    public static boolean prepare(ShaderProgram program, TerrainMultiDrawCommandStream.LayerPacket packet) {
        prepareAttempts++;
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

        uploadedBytes += transforms.remaining();
        successfulPrepares++;
        status = "ready";
        return true;
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

    public record Probe(boolean ready, String status, int blockIndex, int blockBytes) {
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
