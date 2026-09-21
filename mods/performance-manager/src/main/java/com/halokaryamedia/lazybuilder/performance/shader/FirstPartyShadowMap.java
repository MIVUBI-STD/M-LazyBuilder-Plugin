package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/** Render-thread-owned depth-only shadow target. */
public final class FirstPartyShadowMap implements AutoCloseable {
    private int framebufferId;
    private int depthTextureId;
    private int size;

    public void ensureSize(int requestedSize) {
        RenderSystem.assertOnRenderThread();
        int safeSize = Math.max(256, Math.min(4096, requestedSize));
        if (framebufferId != 0 && size == safeSize) return;

        int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

        deleteNow();
        size = safeSize;

        framebufferId = GL30C.glGenFramebuffers();
        if (framebufferId == 0) {
            deleteNow();
            throw new IllegalStateException("OpenGL could not allocate shadow framebuffer.");
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferId);

        depthTextureId = GL11C.glGenTextures();
        if (depthTextureId == 0) {
            deleteNow();
            throw new IllegalStateException("OpenGL could not allocate shadow depth texture.");
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTextureId);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_BORDER);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_BORDER);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer border = stack.floats(1.0F, 1.0F, 1.0F, 1.0F);
            GL11C.glTexParameterfv(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_BORDER_COLOR, border);
        }
        GL11C.glTexImage2D(
                GL11C.GL_TEXTURE_2D,
                0,
                GL30C.GL_DEPTH_COMPONENT32F,
                safeSize,
                safeSize,
                0,
                GL11C.GL_DEPTH_COMPONENT,
                GL11C.GL_FLOAT,
                0L
        );

        GL30C.glFramebufferTexture2D(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_DEPTH_ATTACHMENT,
                GL11C.GL_TEXTURE_2D,
                depthTextureId,
                0
        );
        GL11C.glDrawBuffer(GL11C.GL_NONE);
        GL11C.glReadBuffer(GL11C.GL_NONE);

        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);

        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            deleteNow();
            throw new IllegalStateException(
                    "First-party shadow framebuffer incomplete: 0x"
                            + Integer.toHexString(status)
            );
        }
    }

    public int framebufferId() {
        return framebufferId;
    }

    public int depthTextureId() {
        return depthTextureId;
    }

    public int size() {
        return size;
    }

    @Override
    public void close() {
        if (framebufferId == 0 && depthTextureId == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            int framebuffer = framebufferId;
            int depth = depthTextureId;
            framebufferId = 0;
            depthTextureId = 0;
            size = 0;
            RenderSystem.recordRenderCall(() -> delete(framebuffer, depth));
            return;
        }
        deleteNow();
    }

    private void deleteNow() {
        delete(framebufferId, depthTextureId);
        framebufferId = 0;
        depthTextureId = 0;
        size = 0;
    }

    private static void delete(int framebuffer, int depth) {
        if (depth != 0) GL11C.glDeleteTextures(depth);
        if (framebuffer != 0) GL30C.glDeleteFramebuffers(framebuffer);
    }
}
