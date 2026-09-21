package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Applies LazyBuilder-native composite/final programs to the currently rendered frame.
 *
 * The caller supplies the source framebuffer and dimensions. State is restored before
 * returning so Minecraft's RenderSystem cache observes the same state it had on entry.
 */
public final class FirstPartyShaderPostProcessor implements AutoCloseable {
    private static final String[] POST_PROCESS_PROGRAMS = {"composite", "final"};

    private final FirstPartyShaderFramebuffer scene = new FirstPartyShaderFramebuffer();
    private final FirstPartyShaderFramebuffer scratch = new FirstPartyShaderFramebuffer();
    private final GlState glState = new GlState();
    private int fullscreenVao;

    public boolean render(
            FirstPartyShaderPipeline pipeline,
            int targetFramebuffer,
            int sourceDepthTexture,
            int gbufferTexture1,
            int gbufferTexture2,
            FirstPartyShadowRenderer.Snapshot shadow,
            Matrix4f inverseViewProjection,
            float cameraX,
            float cameraY,
            float cameraZ,
            int width,
            int height,
            float timeSeconds
    ) {
        RenderSystem.assertOnRenderThread();
        if (pipeline == null || width <= 0 || height <= 0) return false;
        boolean hasComposite = pipeline.has("composite");
        boolean hasFinal = pipeline.has("final");
        if (!hasComposite && !hasFinal) return false;
        if (!hasComposite) scratch.release();

        boolean shadowReady = shadow != null && shadow.ready() && shadow.textureId() > 0;
        int highestTextureUnit = highestUsedTextureUnit(pipeline);

        glState.capture(highestTextureUnit);
        try {
            scene.ensureSize(width, height);
            if (hasComposite) scratch.ensureSize(width, height);
            ensureVao();

            copyColor(targetFramebuffer, scene.framebufferId(), width, height);

            FirstPartyShaderFramebuffer current = scene;
            if (hasComposite) {
                runPass(
                        pipeline.program("composite"),
                        current,
                        sourceDepthTexture,
                        gbufferTexture1,
                        gbufferTexture2,
                        shadow,
                        inverseViewProjection,
                        cameraX,
                        cameraY,
                        cameraZ,
                        scratch.framebufferId(),
                        width,
                        height,
                        timeSeconds
                );
                current = scratch;
            }

            if (hasFinal) {
                runPass(
                        pipeline.program("final"),
                        current,
                        sourceDepthTexture,
                        gbufferTexture1,
                        gbufferTexture2,
                        shadow,
                        inverseViewProjection,
                        cameraX,
                        cameraY,
                        cameraZ,
                        targetFramebuffer,
                        width,
                        height,
                        timeSeconds
                );
            } else {
                copyColor(current.framebufferId(), targetFramebuffer, width, height);
            }
            return true;
        } finally {
            glState.restore();
        }
    }

    private void runPass(
            FirstPartyShaderProgram program,
            FirstPartyShaderFramebuffer input,
            int sourceDepthTexture,
            int gbufferTexture1,
            int gbufferTexture2,
            FirstPartyShadowRenderer.Snapshot shadow,
            Matrix4f inverseViewProjection,
            float cameraX,
            float cameraY,
            float cameraZ,
            int outputFramebuffer,
            int width,
            int height,
            float timeSeconds
    ) {
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, outputFramebuffer);
        GL11C.glViewport(0, 0, width, height);
        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(false);
        GL11C.glDisable(GL11C.GL_BLEND);
        GL11C.glDisable(GL11C.GL_CULL_FACE);
        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);

        program.bind();

        bindTexture(program, "LazyBuilderColorTexture", 0, input.colorTextureId());
        bindTexture(program, "LazyBuilderDepthTexture", 1, Math.max(0, sourceDepthTexture));
        bindTexture(program, "LazyBuilderGBuffer1", 2, Math.max(0, gbufferTexture1));
        bindTexture(program, "LazyBuilderGBuffer2", 3, Math.max(0, gbufferTexture2));

        boolean shadowReady = shadow != null && shadow.ready() && shadow.textureId() > 0;
        bindTexture(
                program,
                "LazyBuilderShadowTexture",
                4,
                shadowReady ? shadow.textureId() : 0
        );
        if (shadowReady) {
            uploadMatrix(program, "LazyBuilderShadowViewProjection", shadow.lightViewProjection());

            int shadowCenter = program.uniformLocation("LazyBuilderShadowCenter");
            if (shadowCenter >= 0) {
                GL20C.glUniform3f(
                        shadowCenter,
                        shadow.centerX(),
                        shadow.centerY(),
                        shadow.centerZ()
                );
            }

            int shadowResolution = program.uniformLocation("LazyBuilderShadowResolution");
            if (shadowResolution >= 0) {
                GL20C.glUniform1f(shadowResolution, shadow.resolution());
            }
        }

        int shadowReadyLocation = program.uniformLocation("LazyBuilderShadowReady");
        if (shadowReadyLocation >= 0) GL20C.glUniform1i(shadowReadyLocation, shadowReady ? 1 : 0);

        if (inverseViewProjection != null) {
            uploadMatrix(program, "LazyBuilderInverseViewProjection", inverseViewProjection);
        }
        int cameraPosition = program.uniformLocation("LazyBuilderCameraPosition");
        if (cameraPosition >= 0) {
            GL20C.glUniform3f(cameraPosition, cameraX, cameraY, cameraZ);
        }

        int resolution = program.uniformLocation("LazyBuilderResolution");
        if (resolution >= 0) GL20C.glUniform2f(resolution, width, height);

        int time = program.uniformLocation("LazyBuilderTime");
        if (time >= 0) GL20C.glUniform1f(time, timeSeconds);

        GL30C.glBindVertexArray(fullscreenVao);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
    }

    private static int highestUsedTextureUnit(
            FirstPartyShaderPipeline pipeline
    ) {
        int highest = 0;
        for (String name : POST_PROCESS_PROGRAMS) {
            FirstPartyShaderProgram program = pipeline.program(name);
            if (program == null) continue;
            if (program.uniformLocation("LazyBuilderDepthTexture") >= 0) {
                highest = Math.max(highest, 1);
            }
            if (program.uniformLocation("LazyBuilderGBuffer1") >= 0) {
                highest = Math.max(highest, 2);
            }
            if (program.uniformLocation("LazyBuilderGBuffer2") >= 0) {
                highest = Math.max(highest, 3);
            }
            if (program.uniformLocation("LazyBuilderShadowTexture") >= 0) {
                highest = Math.max(highest, 4);
            }
        }
        return highest;
    }

    private static void uploadMatrix(
            FirstPartyShaderProgram program,
            String uniform,
            Matrix4f matrix
    ) {
        int location = program.uniformLocation(uniform);
        if (location < 0 || matrix == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            matrix.get(values);
            GL20C.glUniformMatrix4fv(location, false, values);
        }
    }

    private static void bindTexture(
            FirstPartyShaderProgram program,
            String uniform,
            int unit,
            int texture
    ) {
        int location = program.uniformLocation(uniform);
        if (location < 0) return;
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GL20C.glUniform1i(location, unit);
    }

    private static void copyColor(
            int source,
            int target,
            int width,
            int height
    ) {
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, source);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
        GL30C.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GL11C.GL_COLOR_BUFFER_BIT,
                GL11C.GL_NEAREST
        );
    }

    private void ensureVao() {
        if (fullscreenVao == 0) fullscreenVao = GL30C.glGenVertexArrays();
    }

    @Override
    public void close() {
        scene.close();
        scratch.close();
        if (fullscreenVao == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            int vao = fullscreenVao;
            fullscreenVao = 0;
            RenderSystem.recordRenderCall(() -> GL30C.glDeleteVertexArrays(vao));
            return;
        }
        GL30C.glDeleteVertexArrays(fullscreenVao);
        fullscreenVao = 0;
    }

    private static final class GlState {
        private int readFramebuffer;
        private int drawFramebuffer;
        private int program;
        private int vao;
        private int activeTexture;
        private int texture0;
        private int texture1;
        private int texture2;
        private int texture3;
        private int texture4;
        private boolean depthTest;
        private boolean depthMask;
        private boolean blend;
        private boolean cull;
        private boolean scissor;
        private int viewportX;
        private int viewportY;
        private int viewportWidth;
        private int viewportHeight;

        void capture(int highestTextureUnit) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
                viewportX = viewport.get(0);
                viewportY = viewport.get(1);
                viewportWidth = viewport.get(2);
                viewportHeight = viewport.get(3);
            }

            activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int boundedHighest = Math.max(0, Math.min(4, highestTextureUnit));

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            texture0 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

            texture1 = -1;
            texture2 = -1;
            texture3 = -1;
            texture4 = -1;
            if (boundedHighest >= 1) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
                texture1 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            }
            if (boundedHighest >= 2) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE2);
                texture2 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            }
            if (boundedHighest >= 3) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE3);
                texture3 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            }
            if (boundedHighest >= 4) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE4);
                texture4 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            }
            GL13C.glActiveTexture(activeTexture);

            readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            vao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
            depthMask = GL11C.glGetInteger(GL11C.GL_DEPTH_WRITEMASK) != 0;
            blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
            cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
            scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        }

        void restore() {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vao);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture0);
            if (texture1 >= 0) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture1);
            }
            if (texture2 >= 0) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE2);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture2);
            }
            if (texture3 >= 0) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE3);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture3);
            }
            if (texture4 >= 0) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE4);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture4);
            }
            GL13C.glActiveTexture(activeTexture);

            set(GL11C.GL_DEPTH_TEST, depthTest);
            GL11C.glDepthMask(depthMask);
            set(GL11C.GL_BLEND, blend);
            set(GL11C.GL_CULL_FACE, cull);
            set(GL11C.GL_SCISSOR_TEST, scissor);
            GL11C.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) GL11C.glEnable(capability);
            else GL11C.glDisable(capability);
        }
    }
}
