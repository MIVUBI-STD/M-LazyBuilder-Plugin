package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

/** Render-thread-owned HDR color framebuffer for first-party shader passes. */
public final class FirstPartyShaderFramebuffer implements AutoCloseable {
    private int framebufferId;
    private int colorTextureId;
    private int width;
    private int height;

    public void ensureSize(int width, int height) {
        RenderSystem.assertOnRenderThread();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (framebufferId != 0 && this.width == safeWidth && this.height == safeHeight) return;

        int oldFramebuffer = framebufferId;
        int oldColorTexture = colorTextureId;
        int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

        deleteNow();
        this.width = safeWidth;
        this.height = safeHeight;

        try {
            framebufferId = GL30C.glGenFramebuffers();
            if (framebufferId == 0) {
                throw new IllegalStateException("OpenGL could not allocate shader framebuffer.");
            }
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferId);

            colorTextureId = GL11C.glGenTextures();
            if (colorTextureId == 0) {
                throw new IllegalStateException("OpenGL could not allocate shader color texture.");
            }
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, colorTextureId);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(
                    GL11C.GL_TEXTURE_2D,
                    0,
                    GL30C.GL_RGBA16F,
                    safeWidth,
                    safeHeight,
                    0,
                    GL11C.GL_RGBA,
                    GL30C.GL_HALF_FLOAT,
                    0L
            );
            GL30C.glFramebufferTexture2D(
                    GL30C.GL_FRAMEBUFFER,
                    GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D,
                    colorTextureId,
                    0
            );

            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException(
                        "First-party shader framebuffer incomplete: 0x"
                                + Integer.toHexString(status)
                );
            }
        } catch (RuntimeException error) {
            deleteNow();
            throw error;
        } finally {
            int restoreTexture = previousTexture == oldColorTexture ? 0 : previousTexture;
            int restoreRead = previousReadFramebuffer == oldFramebuffer ? 0 : previousReadFramebuffer;
            int restoreDraw = previousDrawFramebuffer == oldFramebuffer ? 0 : previousDrawFramebuffer;

            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, Math.max(0, restoreTexture));
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, Math.max(0, restoreRead));
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, Math.max(0, restoreDraw));
        }
    }

    public int framebufferId() {
        return framebufferId;
    }

    public int colorTextureId() {
        return colorTextureId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void release() {
        if (framebufferId == 0 && colorTextureId == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::release);
            return;
        }
        deleteNow();
    }

    @Override
    public void close() {
        if (framebufferId == 0 && colorTextureId == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            int fbo = framebufferId;
            int color = colorTextureId;
            framebufferId = 0;
            colorTextureId = 0;
            width = 0;
            height = 0;
            RenderSystem.recordRenderCall(() -> delete(fbo, color));
            return;
        }
        deleteNow();
    }

    private void deleteNow() {
        delete(framebufferId, colorTextureId);
        framebufferId = 0;
        colorTextureId = 0;
        width = 0;
        height = 0;
    }

    private static void delete(int framebuffer, int color) {
        if (color != 0) GL11C.glDeleteTextures(color);
        if (framebuffer != 0) GL30C.glDeleteFramebuffers(framebuffer);
    }
}
