package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;

import java.util.IdentityHashMap;

/** Captures the vanilla draw state required to address terrain data from a shared arena. */
public final class TerrainArenaDrawStateRegistry {
    private final IdentityHashMap<VertexBuffer, DrawState> states = new IdentityHashMap<>();

    public synchronized void recordVertexUpload(
            VertexBuffer buffer,
            BuiltBuffer.DrawParameters parameters,
            int vertexPayloadBytes,
            int indexPayloadBytes
    ) {
        if (buffer == null || parameters == null) return;
        states.put(buffer, new DrawState(
                parameters.format(),
                parameters.vertexCount(),
                parameters.indexCount(),
                parameters.mode(),
                parameters.indexType(),
                Math.max(0, vertexPayloadBytes),
                Math.max(0, indexPayloadBytes)
        ));
    }

    public synchronized void recordIndexUpload(VertexBuffer buffer, int indexPayloadBytes) {
        if (buffer == null) return;
        DrawState current = states.get(buffer);
        if (current == null) return;
        states.put(buffer, current.withIndexPayload(Math.max(0, indexPayloadBytes)));
    }

    public synchronized DrawState state(VertexBuffer buffer) {
        return buffer == null ? null : states.get(buffer);
    }

    public synchronized long requiredAllocationBytes(VertexBuffer buffer) {
        DrawState state = state(buffer);
        return state == null ? 0L : state.requiredAllocationBytes();
    }

    public synchronized void release(VertexBuffer buffer) {
        if (buffer != null) states.remove(buffer);
    }

    public synchronized void clear() {
        states.clear();
    }

    public record DrawState(
            VertexFormat format,
            int vertexCount,
            int indexCount,
            VertexFormat.DrawMode mode,
            VertexFormat.IndexType indexType,
            int vertexPayloadBytes,
            int indexPayloadBytes
    ) {
        public long vertexAllocationBytes() {
            return TerrainRegionArenaPolicy.alignedSize(vertexPayloadBytes);
        }

        public long indexAllocationBytes() {
            return TerrainRegionArenaPolicy.alignedSize(indexPayloadBytes);
        }

        public long requiredAllocationBytes() {
            long vertex = vertexAllocationBytes();
            long index = indexAllocationBytes();
            return vertex > Long.MAX_VALUE - index ? Long.MAX_VALUE : vertex + index;
        }

        public long indexOffsetWithinAllocation() {
            return vertexAllocationBytes();
        }

        public boolean drawable() {
            return format != null
                    && mode != null
                    && indexType != null
                    && format.getVertexSizeByte() > 0
                    && vertexCount > 0
                    && indexCount > 0
                    && vertexPayloadBytes > 0;
        }

        private DrawState withIndexPayload(int bytes) {
            return new DrawState(format, vertexCount, indexCount, mode, indexType, vertexPayloadBytes, bytes);
        }
    }
}
