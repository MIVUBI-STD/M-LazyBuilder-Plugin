package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;

import java.util.concurrent.CompletableFuture;

/** Queue task that can upload vertex or index data while reusing an already-bound VertexBuffer. */
public final class ChunkUploadTask implements Runnable {
    private final VertexBuffer buffer;
    private final BuiltBuffer vertexData;
    private final BufferAllocator.CloseableBuffer indexData;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    private ChunkUploadTask(
            VertexBuffer buffer,
            BuiltBuffer vertexData,
            BufferAllocator.CloseableBuffer indexData
    ) {
        this.buffer = buffer;
        this.vertexData = vertexData;
        this.indexData = indexData;
    }

    public static ChunkUploadTask vertex(BuiltBuffer data, VertexBuffer buffer) {
        return new ChunkUploadTask(buffer, data, null);
    }

    public static ChunkUploadTask index(BufferAllocator.CloseableBuffer data, VertexBuffer buffer) {
        return new ChunkUploadTask(buffer, null, data);
    }

    public VertexBuffer buffer() {
        return buffer;
    }

    public CompletableFuture<Void> future() {
        return future;
    }

    public void executeBound() {
        if (future.isDone()) return;
        if (buffer.isClosed()) {
            discard();
            return;
        }

        try {
            if (vertexData != null) {
                buffer.upload(vertexData);
            } else if (indexData != null) {
                buffer.uploadIndexBuffer(indexData);
            }
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    public void discard() {
        if (future.isDone()) return;
        try {
            if (vertexData != null) {
                vertexData.close();
            } else if (indexData != null) {
                indexData.close();
            }
            if (buffer.isClosed()) {
                TerrainGpuResidencyTracker.release(buffer);
            }
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    public void fail(Throwable throwable) {
        future.completeExceptionally(throwable);
    }

    @Override
    public void run() {
        if (buffer.isClosed()) {
            discard();
            return;
        }

        try {
            buffer.bind();
        } catch (Throwable throwable) {
            fail(throwable);
            return;
        }

        try {
            executeBound();
        } finally {
            VertexBuffer.unbind();
        }
    }
}
