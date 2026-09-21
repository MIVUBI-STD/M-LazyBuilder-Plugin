package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

/** Render-thread-owned color+depth framebuffer for first-party shader passes. */
public final class FirstPartyShaderFramebuffer implements AutoCloseable {
    private int framebufferId;
    private int colorTextureId;
    private int depthTextureId;
    private int width;
    private int height;

    public void ensureSize(int width, int height) {
        RenderSystem.assertOnRenderThread();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (framebufferId != 0 && this.width == safeWidth && this.height == safeHeight) return;

        deleteNow();
        this.width = safeWidth;
        this.height = safeHeight;

        framebufferId = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferId);

        colorTextureId = GL11C.glGenTextures();
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

        depthTextureId = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTextureId);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(
                GL11C.GL_TEXTURE_2D,
                0,
                GL14C.GL_DEPTH_COMPONENT24,
                safeWidth,
                safeHeight,
                0,
                GL11C.GL_DEPTH_COMPONENT,
                GL11C.GL_UNSIGNED_INT,
                0L
        );
        GL30C.glFramebufferTexture2D(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_DEPTH_ATTACHMENT,
                GL11C.GL_TEXTURE_2D,
                depthTextureId,
                0
        );

        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);

        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            deleteNow();
            throw new IllegalStateException("First-party shader framebuffer incomplete: 0x"
                    + Integer.toHexString(status));
        }
    }

    public int framebufferId() {
        return framebufferId;
    }

    public int colorTextureId() {
        return colorTextureId;
    }

    public int depthTextureId() {
        return depthTextureId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        if (framebufferId == 0 && colorTextureId == 0 && depthTextureId == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            int fbo = framebufferId;
            int color = colorTextureId;
            int depth = depthTextureId;
            framebufferId = 0;
            colorTextureId = 0;
            depthTextureId = 0;
            width = 0;
            height = 0;
            RenderSystem.recordRenderCall(() -> delete(fbo, color, depth));
            return;
        }
        deleteNow();
    }

    private void deleteNow() {
        delete(framebufferId, colorTextureId, depthTextureId);
        framebufferId = 0;
        colorTextureId = 0;
        depthTextureId = 0;
        width = 0;
        height = 0;
    }

    private static void delete(int framebuffer, int color, int depth) {
        if (color != 0) GL11C.glDeleteTextures(color);
        if (depth != 0) GL11C.glDeleteTextures(depth);
        if (framebuffer != 0) GL30C.glDeleteFramebuffers(framebuffer);
    }
}
