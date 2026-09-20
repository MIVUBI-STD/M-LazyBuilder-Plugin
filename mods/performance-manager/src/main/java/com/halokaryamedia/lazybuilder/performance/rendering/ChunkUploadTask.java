package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** Queue task that can upload vertex or index data while reusing an already-bound VertexBuffer. */
public final class ChunkUploadTask implements Runnable {
    private final Object sessionOwner;
    private final long sessionGeneration;
    private final VertexBuffer buffer;
    private final BuiltBuffer vertexData;
    private final BufferAllocator.CloseableBuffer indexData;
    private final int vertexPayloadBytes;
    private final int indexPayloadBytes;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    private ChunkUploadTask(
            Object sessionOwner,
            long sessionGeneration,
            VertexBuffer buffer,
            BuiltBuffer vertexData,
            BufferAllocator.CloseableBuffer indexData,
            int vertexPayloadBytes,
            int indexPayloadBytes
    ) {
        this.sessionOwner = sessionOwner;
        this.sessionGeneration = sessionGeneration;
        this.buffer = buffer;
        this.vertexData = vertexData;
        this.indexData = indexData;
        this.vertexPayloadBytes = Math.max(0, vertexPayloadBytes);
        this.indexPayloadBytes = Math.max(0, indexPayloadBytes);
    }

    public static ChunkUploadTask vertex(Object sessionOwner, BuiltBuffer data, VertexBuffer buffer) {
        ByteBuffer vertices = data == null ? null : data.getBuffer();
        ByteBuffer sortedIndices = data == null ? null : data.getSortedBuffer();
        long generation = TerrainGpuResidencyTracker.sessionGeneration(sessionOwner);
        return new ChunkUploadTask(
                sessionOwner,
                generation,
                buffer,
                data,
                null,
                remaining(vertices),
                remaining(sortedIndices)
        );
    }

    public static ChunkUploadTask index(Object sessionOwner, BufferAllocator.CloseableBuffer data, VertexBuffer buffer) {
        ByteBuffer indices = data == null ? null : data.getBuffer();
        long generation = TerrainGpuResidencyTracker.sessionGeneration(sessionOwner);
        return new ChunkUploadTask(sessionOwner, generation, buffer, null, data, 0, remaining(indices));
    }

    public VertexBuffer buffer() {
        return buffer;
    }

    public CompletableFuture<Void> future() {
        return future;
    }

    public void executeBound() {
        if (future.isDone()) return;
        if (!TerrainGpuResidencyTracker.ownsSession(sessionOwner)) {
            discard();
            return;
        }
        if (buffer.isClosed()) {
            discard();
            return;
        }

        try {
            if (vertexData != null) {
                TerrainPhysicalArenaManager.prepareForVanillaUpload(buffer);
                TerrainGpuResidencyTracker.recordDrawState(
                        buffer,
                        vertexData.getDrawParameters(),
                        vertexPayloadBytes,
                        indexPayloadBytes
                );
                TerrainGpuResidencyTracker.recordPayload(buffer, vertexPayloadBytes, indexPayloadBytes);
                TerrainPhysicalArenaManager.upload(
                        buffer,
                        vertexData.getBuffer(),
                        vertexData.getSortedBuffer()
                );
                buffer.upload(vertexData);
            } else if (indexData != null) {
                TerrainGpuResidencyTracker.recordIndexDrawState(buffer, indexPayloadBytes);
                TerrainGpuResidencyTracker.recordIndexPayload(buffer, indexPayloadBytes);
                TerrainPhysicalArenaManager.uploadIndex(buffer, indexData.getBuffer());
                buffer.uploadIndexBuffer(indexData);
            }
            future.complete(null);
        } catch (Throwable throwable) {
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
        if (future.isDone()) return;
        closePayload();
        future.completeExceptionally(throwable);
    }

    private void closePayload() {
        try {
            if (vertexData != null) {
                vertexData.close();
            } else if (indexData != null) {
                indexData.close();
            }
        } catch (Throwable ignored) {
            // Preserve the original upload/bind failure as the authoritative future cause.
        }
    }

    @Override
    public void run() {
        if (!TerrainGpuResidencyTracker.ownsSession(sessionOwner)) {
            discard();
            return;
        }
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
