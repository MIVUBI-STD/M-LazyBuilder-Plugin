package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GpuBuffer;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;

import java.nio.ByteBuffer;

/** Small render-thread-owned GL buffer wrapper used by shared terrain arenas. */
final class TerrainPhysicalBuffer implements AutoCloseable {
    static final int VERTICES = 34962;
    static final int INDICES = 34963;
    static final int UNIFORM = 35345;
    private static final int COPY_READ = 36662;
    private static final int COPY_WRITE = 36663;
    private static final int DYNAMIC_DRAW = 35048;

    private final int target;
    private final int handle;
    private int size;
    private boolean closed;

    TerrainPhysicalBuffer(int target, int size) {
        RenderSystem.assertOnRenderThread();
        if (size < 0) throw new IllegalArgumentException("Negative buffer size");
        this.target = target;

        int generated = GlStateManager._glGenBuffers();
        try {
            GlStateManager._glBindBuffer(target, generated);
            GlStateManager._glBufferData(target, (long) size, DYNAMIC_DRAW);
        } catch (RuntimeException ex) {
            GlStateManager._glDeleteBuffers(generated);
            throw ex;
        }

        this.handle = generated;
        this.size = size;
    }

    int size() {
        return size;
    }

    void bind() {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        GlStateManager._glBindBuffer(target, handle);
    }

    void bindBase(int bindingIndex) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        GL30C.glBindBufferBase(target, bindingIndex, handle);
    }

    void upload(ByteBuffer source, int offset) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (source == null) throw new IllegalArgumentException("Missing upload source");
        int bytes = source.remaining();
        if (offset < 0 || bytes < 0 || offset > size - bytes) {
            throw new IllegalArgumentException("Upload exceeds physical terrain buffer");
        }
        bind();
        GlStateManager._glBufferSubData(target, offset, source);
    }

    void copyTo(TerrainPhysicalBuffer destination, int sourceOffset, int destinationOffset, int bytes) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (destination == null) throw new IllegalArgumentException("Missing copy destination");
        destination.ensureOpen();
        if (sourceOffset < 0 || destinationOffset < 0 || bytes < 0
                || sourceOffset > this.size - bytes
                || destinationOffset > destination.size - bytes) {
            throw new IllegalArgumentException("Copy exceeds physical terrain buffer");
        }
        if (bytes == 0) return;

        GlStateManager._glBindBuffer(COPY_READ, this.handle);
        GlStateManager._glBindBuffer(COPY_WRITE, destination.handle);
        GL31C.glCopyBufferSubData(COPY_READ, COPY_WRITE, sourceOffset, destinationOffset, bytes);
    }

    void copyTo(GpuBuffer destination, int sourceOffset, int destinationOffset, int bytes) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (destination == null) throw new IllegalArgumentException("Missing GPU copy destination");
        if (sourceOffset < 0 || destinationOffset < 0 || bytes < 0
                || sourceOffset > this.size - bytes
                || destinationOffset > destination.size - bytes) {
            throw new IllegalArgumentException("Copy exceeds terrain or vanilla GPU buffer");
        }
        if (bytes == 0) return;
        GlStateManager._glBindBuffer(COPY_READ, this.handle);
        GlStateManager._glBindBuffer(COPY_WRITE, destination.handle);
        GL31C.glCopyBufferSubData(COPY_READ, COPY_WRITE, sourceOffset, destinationOffset, bytes);
    }

    void resizeDiscarding(int newSize) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (newSize < 0) throw new IllegalArgumentException("Negative buffer size");
        GlStateManager._glBindBuffer(target, handle);
        GlStateManager._glBufferData(target, (long) newSize, DYNAMIC_DRAW);
        this.size = newSize;
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        closed = true;
        GlStateManager._glDeleteBuffers(handle);
        size = 0;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Physical terrain buffer already closed");
    }
}
