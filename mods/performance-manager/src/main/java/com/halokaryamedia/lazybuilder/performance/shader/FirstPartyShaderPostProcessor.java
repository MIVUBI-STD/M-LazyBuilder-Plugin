package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

/**
 * Applies LazyBuilder-native composite/final programs to the currently rendered frame.
 *
 * The caller supplies the source framebuffer and dimensions. State is restored before
 * returning so Minecraft's RenderSystem cache observes the same state it had on entry.
 */
public final class FirstPartyShaderPostProcessor implements AutoCloseable {
    private final FirstPartyShaderFramebuffer scene = new FirstPartyShaderFramebuffer();
    private final FirstPartyShaderFramebuffer scratch = new FirstPartyShaderFramebuffer();
    private int fullscreenVao;

    public boolean render(
            FirstPartyShaderPipeline pipeline,
            int targetFramebuffer,
            int sourceDepthTexture,
            int width,
            int height,
            float timeSeconds
    ) {
        RenderSystem.assertOnRenderThread();
        if (pipeline == null || width <= 0 || height <= 0) return false;
        boolean hasComposite = pipeline.has("composite");
        boolean hasFinal = pipeline.has("final");
        if (!hasComposite && !hasFinal) return false;

        GlState state = GlState.capture();
        try {
            scene.ensureSize(width, height);
            scratch.ensureSize(width, height);
            ensureVao();

            copyColor(targetFramebuffer, scene.framebufferId(), width, height);

            FirstPartyShaderFramebuffer current = scene;
            if (hasComposite) {
                runPass(
                        pipeline.program("composite"),
                        current,
                        sourceDepthTexture,
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
            state.restore();
        }
    }

    private void runPass(
            FirstPartyShaderProgram program,
            FirstPartyShaderFramebuffer input,
            int sourceDepthTexture,
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
        if (sourceDepthTexture > 0) {
            bindTexture(program, "LazyBuilderDepthTexture", 1, sourceDepthTexture);
        }

        int resolution = program.uniformLocation("LazyBuilderResolution");
        if (resolution >= 0) GL20C.glUniform2f(resolution, width, height);

        int time = program.uniformLocation("LazyBuilderTime");
        if (time >= 0) GL20C.glUniform1f(time, timeSeconds);

        GL30C.glBindVertexArray(fullscreenVao);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
    }

    private static void bindTexture(
            FirstPartyShaderProgram program,
            String uniform,
            int unit,
            int texture
    ) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        int location = program.uniformLocation(uniform);
        if (location >= 0) GL20C.glUniform1i(location, unit);
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

    private record GlState(
            int readFramebuffer,
            int drawFramebuffer,
            int program,
            int vao,
            int activeTexture,
            int texture0,
            int texture1,
            boolean depthTest,
            boolean depthMask,
            boolean blend,
            boolean cull,
            boolean scissor,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        static GlState capture() {
            int[] viewport = new int[4];
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);

            int activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int texture0 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            int texture1 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL13C.glActiveTexture(activeTexture);

            return new GlState(
                    GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),
                    GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    activeTexture,
                    texture0,
                    texture1,
                    GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    GL11C.glGetInteger(GL11C.GL_DEPTH_WRITEMASK) != 0,
                    GL11C.glIsEnabled(GL11C.GL_BLEND),
                    GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST),
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3]
            );
        }

        void restore() {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vao);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture1);
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
