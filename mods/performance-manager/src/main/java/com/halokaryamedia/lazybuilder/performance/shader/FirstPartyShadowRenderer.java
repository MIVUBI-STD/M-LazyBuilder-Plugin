package com.halokaryamedia.lazybuilder.performance.shader;

import com.halokaryamedia.lazybuilder.performance.rendering.TerrainPhysicalArenaManager;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainVisibleDrawSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Single-cascade native terrain shadow renderer.
 *
 * V1 deliberately renders solid terrain only. Cutout terrain is excluded until
 * block-atlas alpha testing is bound explicitly; treating leaves/fences as opaque
 * would be visually incorrect.
 */
public final class FirstPartyShadowRenderer implements AutoCloseable {
    private static final int DEFAULT_RESOLUTION = 2048;
    private static final float HALF_EXTENT = 192.0F;
    private static final float LIGHT_DISTANCE = 256.0F;
    private static final float DEPTH_RANGE = 640.0F;
    private static final float CENTER_SNAP = 4.0F;

    private final FirstPartyShadowMap shadowMap = new FirstPartyShadowMap();
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public Snapshot render(
            FirstPartyShaderPipeline pipeline,
            double cameraX,
            double cameraY,
            double cameraZ,
            long timeOfDay
    ) {
        RenderSystem.assertOnRenderThread();
        if (pipeline == null || !pipeline.has("shadow")) {
            snapshot = Snapshot.EMPTY;
            return snapshot;
        }

        TerrainVisibleDrawSnapshot.Snapshot visible = TerrainVisibleDrawSnapshot.current();
        if (visible.isEmpty()) {
            snapshot = new Snapshot(
                    false,
                    "no-visible-terrain",
                    0,
                    DEFAULT_RESOLUTION,
                    new Matrix4f(),
                    0.0F,
                    0.0F,
                    0.0F,
                    0,
                    0
            );
            return snapshot;
        }

        FirstPartyShaderProgram program = pipeline.program("shadow");
        if (program == null) {
            snapshot = Snapshot.EMPTY;
            return snapshot;
        }

        GlState state = GlState.capture();
        int drawn = 0;
        int skipped = 0;

        float centerX = snap((float) cameraX);
        float centerY = snap((float) cameraY);
        float centerZ = snap((float) cameraZ);
        Matrix4f lightViewProjection = lightViewProjection(timeOfDay);

        try {
            shadowMap.ensureSize(DEFAULT_RESOLUTION);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, shadowMap.framebufferId());
            GL11C.glViewport(0, 0, shadowMap.size(), shadowMap.size());
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthMask(true);
            GL11C.glDepthFunc(GL11C.GL_LEQUAL);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glEnable(GL11C.GL_CULL_FACE);
            GL11C.glCullFace(GL11C.GL_BACK);
            GL11C.glClearDepth(1.0D);
            GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT);

            program.bind();
            uploadMatrix(program, "LazyBuilderShadowViewProjection", lightViewProjection);

            int offsetLocation = program.uniformLocation("LazyBuilderModelOffset");
            if (offsetLocation < 0) {
                throw new IllegalStateException("Shadow program lost LazyBuilderModelOffset");
            }

            for (TerrainVisibleDrawSnapshot.Entry entry : visible.entries()) {
                // Only fully opaque terrain in the first native shadow stage.
                if (entry.layerSlot() != 0 || !entry.usable()) {
                    skipped++;
                    continue;
                }

                VertexBuffer buffer = entry.buffer();
                GL20C.glUniform3f(
                        offsetLocation,
                        entry.originX() - centerX,
                        entry.originY() - centerY,
                        entry.originZ() - centerZ
                );

                boolean rendered = false;
                if (TerrainPhysicalArenaManager.bind(buffer)) {
                    rendered = TerrainPhysicalArenaManager.drawSecondary(buffer);
                }

                if (!rendered && !TerrainPhysicalArenaManager.isExclusive(buffer)) {
                    TerrainPhysicalArenaManager.noteExternalBind();
                    buffer.bind();
                    buffer.draw();
                    rendered = true;
                }

                if (rendered) drawn++;
                else skipped++;
            }

            FirstPartyShaderProgram.unbind();
            TerrainPhysicalArenaManager.noteExternalBind();

            snapshot = new Snapshot(
                    drawn > 0,
                    drawn > 0 ? "ready" : "no-drawable-terrain",
                    shadowMap.depthTextureId(),
                    shadowMap.size(),
                    new Matrix4f(lightViewProjection),
                    centerX,
                    centerY,
                    centerZ,
                    drawn,
                    skipped
            );
            return snapshot;
        } catch (RuntimeException error) {
            TerrainPhysicalArenaManager.noteExternalBind();
            snapshot = new Snapshot(
                    false,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    0,
                    shadowMap.size(),
                    new Matrix4f(),
                    centerX,
                    centerY,
                    centerZ,
                    drawn,
                    skipped
            );
            return snapshot;
        } finally {
            state.restore();
        }
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private static Matrix4f lightViewProjection(long timeOfDay) {
        float day = Math.floorMod(timeOfDay, 24000L) / 24000.0F;
        float angle = day * ((float) Math.PI * 2.0F) - ((float) Math.PI / 2.0F);

        Vector3f direction = new Vector3f(
                (float) Math.cos(angle),
                (float) Math.sin(angle),
                0.25F
        ).normalize();
        if (direction.y < 0.0F) direction.negate();

        Vector3f eye = new Vector3f(direction).mul(-LIGHT_DISTANCE);
        Vector3f up = Math.abs(direction.y) > 0.95F
                ? new Vector3f(0.0F, 0.0F, 1.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);

        Matrix4f projection = new Matrix4f().setOrtho(
                -HALF_EXTENT,
                HALF_EXTENT,
                -HALF_EXTENT,
                HALF_EXTENT,
                1.0F,
                DEPTH_RANGE
        );
        Matrix4f view = new Matrix4f().lookAt(
                eye.x, eye.y, eye.z,
                0.0F, 0.0F, 0.0F,
                up.x, up.y, up.z
        );
        return projection.mul(view, new Matrix4f());
    }

    private static float snap(float value) {
        return Math.round(value / CENTER_SNAP) * CENTER_SNAP;
    }

    private static void uploadMatrix(
            FirstPartyShaderProgram program,
            String uniform,
            Matrix4f matrix
    ) {
        int location = program.uniformLocation(uniform);
        if (location < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            matrix.get(values);
            GL20C.glUniformMatrix4fv(location, false, values);
        }
    }

    @Override
    public void close() {
        shadowMap.close();
        snapshot = Snapshot.EMPTY;
    }

    public record Snapshot(
            boolean ready,
            String status,
            int textureId,
            int resolution,
            Matrix4f lightViewProjection,
            float centerX,
            float centerY,
            float centerZ,
            int drawnBuffers,
            int skippedBuffers
    ) {
        private static final Snapshot EMPTY = new Snapshot(
                false,
                "not-configured",
                0,
                0,
                new Matrix4f(),
                0.0F,
                0.0F,
                0.0F,
                0,
                0
        );

        public Snapshot {
            status = status == null ? "" : status;
            lightViewProjection = lightViewProjection == null
                    ? new Matrix4f()
                    : new Matrix4f(lightViewProjection);
        }
    }

    private record GlState(
            int drawFramebuffer,
            int program,
            int vao,
            boolean depthTest,
            boolean depthMask,
            int depthFunc,
            boolean blend,
            boolean cull,
            int cullFace,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        static GlState capture() {
            int[] viewport = new int[4];
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            return new GlState(
                    GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    GL11C.glGetInteger(GL11C.GL_DEPTH_WRITEMASK) != 0,
                    GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC),
                    GL11C.glIsEnabled(GL11C.GL_BLEND),
                    GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE),
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3]
            );
        }

        void restore() {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vao);
            if (depthTest) GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthMask(depthMask);
            GL11C.glDepthFunc(depthFunc);
            if (blend) GL11C.glEnable(GL11C.GL_BLEND);
            else GL11C.glDisable(GL11C.GL_BLEND);
            if (cull) GL11C.glEnable(GL11C.GL_CULL_FACE);
            else GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glCullFace(cullFace);
            GL11C.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }
}
