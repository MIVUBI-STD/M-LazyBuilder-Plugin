package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Attaches up to two LazyBuilder-owned HDR color targets to Minecraft's existing
 * world framebuffer for the duration of world rendering.
 *
 * It never replaces a foreign attachment. If attachment 1/2 is already owned,
 * G-buffer output fails open for that frame.
 */
public final class FirstPartyShaderGBuffer implements AutoCloseable {
    private static int cachedMaxColorAttachments = -1;
    private static int cachedMaxDrawBuffers = -1;

    private final int[] textures = new int[2];
    private int width;
    private int height;
    private int activeFramebuffer;
    private int activeCount;
    private final int[] previousDrawBuffers = new int[3];
    private int previousDrawBufferCount;
    private long staleFrameRecoveries;
    private String status = "inactive";

    public boolean begin(
            int framebuffer,
            int width,
            int height,
            int requestedAttachments
    ) {
        RenderSystem.assertOnRenderThread();
        if (activeFramebuffer != 0 && activeCount > 0) {
            recoverStaleFrame();
        }
        int count = Math.max(0, Math.min(2, requestedAttachments));
        if (framebuffer <= 0 || count <= 0 || width <= 0 || height <= 0) {
            status = "not-requested";
            return false;
        }

        ensureCapabilities();
        if (cachedMaxColorAttachments < count + 1 || cachedMaxDrawBuffers < count + 1) {
            status = "insufficient-attachments";
            return false;
        }

        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);

        int attachedCount = 0;
        boolean drawBuffersChanged = false;
        try {
            captureDrawBuffers(count + 1);
            int draw0 = previousDrawBuffers[0];
            if (draw0 != GL30C.GL_COLOR_ATTACHMENT0) {
                status = "foreign-draw-buffer-layout";
                previousDrawBufferCount = 0;
                return false;
            }

            for (int index = 0; index < count; index++) {
                int attachment = GL30C.GL_COLOR_ATTACHMENT1 + index;
                int objectType = GL30C.glGetFramebufferAttachmentParameteri(
                        GL30C.GL_DRAW_FRAMEBUFFER,
                        attachment,
                        GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                );
                if (objectType != GL11C.GL_NONE) {
                    status = "attachment-owned:" + (index + 1);
                    previousDrawBufferCount = 0;
                    return false;
                }
            }

            ensureTextures(width, height, count);
            for (int index = 0; index < count; index++) {
                GL30C.glFramebufferTexture2D(
                        GL30C.GL_DRAW_FRAMEBUFFER,
                        GL30C.GL_COLOR_ATTACHMENT1 + index,
                        GL11C.GL_TEXTURE_2D,
                        textures[index],
                        0
                );
                attachedCount++;
            }

            setDrawBuffers(count);
            drawBuffersChanged = true;
            int framebufferStatus = GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER);
            if (framebufferStatus != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                detachOwnedAttachments(framebuffer, attachedCount);
                restoreDrawBuffers();
                status = "framebuffer-incomplete:0x" + Integer.toHexString(framebufferStatus);
                return false;
            }

            clearAttachments(count);
            activeFramebuffer = framebuffer;
            activeCount = count;
            status = "active";
            return true;
        } catch (RuntimeException error) {
            if (attachedCount > 0) {
                detachOwnedAttachments(framebuffer, attachedCount);
            }
            if (drawBuffersChanged || previousDrawBufferCount > 0) {
                restoreDrawBuffers();
            }
            status = "begin-error";
            throw error;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    public Snapshot end(int framebuffer) {
        RenderSystem.assertOnRenderThread();
        if (activeFramebuffer == 0 || activeCount <= 0) {
            return new Snapshot(false, status, 0, 0, 0, staleFrameRecoveries);
        }

        int source = activeFramebuffer;
        int count = activeCount;
        int texture1 = count >= 1 ? textures[0] : 0;
        int texture2 = count >= 2 ? textures[1] : 0;

        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            detachOwnedAttachments(source, count);
            restoreDrawBuffers();
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            activeFramebuffer = 0;
            activeCount = 0;
            status = framebuffer == source ? "ready" : "target-changed";
        }

        return new Snapshot(true, status, count, texture1, texture2, staleFrameRecoveries);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                activeFramebuffer != 0,
                status,
                activeCount,
                activeCount >= 1 ? textures[0] : 0,
                activeCount >= 2 ? textures[1] : 0,
                staleFrameRecoveries
        );
    }

    private static void ensureCapabilities() {
        if (cachedMaxColorAttachments >= 0 && cachedMaxDrawBuffers >= 0) return;
        cachedMaxColorAttachments = GL11C.glGetInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
        cachedMaxDrawBuffers = GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
    }

    private void ensureTextures(int width, int height, int count) {
        if (this.width == width && this.height == height && texturesReady(count)) {
            releaseUnusedTextures(count);
            return;
        }

        int oldTexture0 = textures[0];
        int oldTexture1 = textures[1];
        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        deleteTextures();
        this.width = width;
        this.height = height;

        try {
            for (int index = 0; index < count; index++) {
                int texture = GL11C.glGenTextures();
                if (texture == 0) {
                    throw new IllegalStateException(
                            "OpenGL could not allocate GBuffer texture " + (index + 1)
                    );
                }
                textures[index] = texture;
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
                GL11C.glTexImage2D(
                        GL11C.GL_TEXTURE_2D,
                        0,
                        GL30C.GL_RGBA16F,
                        width,
                        height,
                        0,
                        GL11C.GL_RGBA,
                        GL30C.GL_HALF_FLOAT,
                        0L
                );
            }
        } catch (RuntimeException error) {
            deleteTextures();
            this.width = 0;
            this.height = 0;
            throw error;
        } finally {
            int restoreTexture = (previousTexture == oldTexture0 || previousTexture == oldTexture1)
                    ? 0
                    : previousTexture;
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, Math.max(0, restoreTexture));
        }
    }

    private void releaseUnusedTextures(int count) {
        for (int index = Math.max(0, count); index < textures.length; index++) {
            if (textures[index] != 0) {
                GL11C.glDeleteTextures(textures[index]);
                textures[index] = 0;
            }
        }
    }

    private boolean texturesReady(int count) {
        for (int index = 0; index < count; index++) {
            if (textures[index] == 0) return false;
        }
        return true;
    }

    private static void setDrawBuffers(int extraAttachments) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffers = stack.mallocInt(extraAttachments + 1);
            buffers.put(GL30C.GL_COLOR_ATTACHMENT0);
            for (int index = 0; index < extraAttachments; index++) {
                buffers.put(GL30C.GL_COLOR_ATTACHMENT1 + index);
            }
            buffers.flip();
            GL20C.glDrawBuffers(buffers);
        }
    }

    private static void clearAttachments(int count) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer zero = stack.floats(0.0F, 0.0F, 0.0F, 0.0F);
            for (int index = 0; index < count; index++) {
                zero.position(0);
                GL30C.glClearBufferfv(GL11C.GL_COLOR, index + 1, zero);
            }
        }
    }

    private void captureDrawBuffers(int count) {
        previousDrawBufferCount = Math.max(1, Math.min(previousDrawBuffers.length, count));
        for (int index = 0; index < previousDrawBufferCount; index++) {
            previousDrawBuffers[index] = GL11C.glGetInteger(GL20C.GL_DRAW_BUFFER0 + index);
        }
    }

    private void restoreDrawBuffers() {
        int count = previousDrawBufferCount;
        if (count <= 0) {
            setDrawBuffers(0);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffers = stack.mallocInt(count);
            for (int index = 0; index < count; index++) {
                buffers.put(previousDrawBuffers[index]);
            }
            buffers.flip();
            GL20C.glDrawBuffers(buffers);
        }
        previousDrawBufferCount = 0;
    }

    private static void detachOwnedAttachments(int framebuffer, int count) {
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        for (int index = 0; index < count; index++) {
            GL30C.glFramebufferTexture2D(
                    GL30C.GL_DRAW_FRAMEBUFFER,
                    GL30C.GL_COLOR_ATTACHMENT1 + index,
                    GL11C.GL_TEXTURE_2D,
                    0,
                    0
            );
        }
    }

    private void recoverStaleFrame() {
        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            detachOwnedAttachments(activeFramebuffer, activeCount);
            restoreDrawBuffers();
            staleFrameRecoveries++;
            status = "stale-frame-recovered";
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            activeFramebuffer = 0;
            activeCount = 0;
        }
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::close);
            return;
        }

        if (activeFramebuffer != 0 && activeCount > 0) {
            recoverStaleFrame();
        }
        deleteTextures();
        activeFramebuffer = 0;
        activeCount = 0;
        previousDrawBufferCount = 0;
        width = 0;
        height = 0;
        status = "closed";
    }

    private void deleteTextures() {
        for (int index = 0; index < textures.length; index++) {
            if (textures[index] != 0) {
                GL11C.glDeleteTextures(textures[index]);
                textures[index] = 0;
            }
        }
    }

    public record Snapshot(
            boolean active,
            String status,
            int attachmentCount,
            int texture1,
            int texture2,
            long staleFrameRecoveries
    ) {
        public Snapshot {
            status = status == null ? "" : status;
        }
    }
}
