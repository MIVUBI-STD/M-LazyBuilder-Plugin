package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** Queue task that can upload vertex or index data while reusing an already-bound VertexBuffer. */
public final class ChunkUploadTask implements Runnable {
    private final VertexBuffer buffer;
    private final BuiltBuffer vertexData;
    private final BufferAllocator.CloseableBuffer indexData;
    private final int vertexPayloadBytes;
    private final int indexPayloadBytes;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    private ChunkUploadTask(
            VertexBuffer buffer,
            BuiltBuffer vertexData,
            BufferAllocator.CloseableBuffer indexData,
            int vertexPayloadBytes,
            int indexPayloadBytes
    ) {
        this.buffer = buffer;
        this.vertexData = vertexData;
        this.indexData = indexData;
        this.vertexPayloadBytes = Math.max(0, vertexPayloadBytes);
        this.indexPayloadBytes = Math.max(0, indexPayloadBytes);
    }

    public static ChunkUploadTask vertex(BuiltBuffer data, VertexBuffer buffer) {
        ByteBuffer vertices = data == null ? null : data.getBuffer();
        ByteBuffer sortedIndices = data == null ? null : data.getSortedBuffer();
        return new ChunkUploadTask(buffer, data, null, remaining(vertices), remaining(sortedIndices));
    }

    public static ChunkUploadTask index(BufferAllocator.CloseableBuffer data, VertexBuffer buffer) {
        ByteBuffer indices = data == null ? null : data.getBuffer();
        return new ChunkUploadTask(buffer, null, data, 0, remaining(indices));
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
                // Prepare logical arena ownership while the source ByteBuffer is still alive. The
                // physical mirror is best-effort; vanilla upload remains the correctness fallback.
                TerrainGpuResidencyTracker.recordDrawState(
                        buffer,
                        vertexData.getDrawParameters(),
                        vertexPayloadBytes,
                        indexPayloadBytes
                );
                TerrainGpuResidencyTracker.recordPayload(buffer, vertexPayloadBytes, indexPayloadBytes);
                TerrainPhysicalArenaManager.uploadVertex(buffer, vertexData.getBuffer());
                buffer.upload(vertexData);
            } else if (indexData != null) {
                // A separately uploaded/sorted index stream leaves the base-vertex-only subset.
                TerrainGpuResidencyTracker.recordIndexDrawState(buffer, indexPayloadBytes);
                TerrainGpuResidencyTracker.recordIndexPayload(buffer, indexPayloadBytes);
                TerrainPhysicalArenaManager.release(buffer);
                buffer.uploadIndexBuffer(indexData);
            }
            future.complete(null);
        } catch (Throwable throwable) {
            TerrainPhysicalArenaManager.release(buffer);
            TerrainGpuResidencyTracker.release(buffer);
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

    private static int remaining(ByteBuffer buffer) {
        return buffer == null ? 0 : buffer.remaining();
    }
}
