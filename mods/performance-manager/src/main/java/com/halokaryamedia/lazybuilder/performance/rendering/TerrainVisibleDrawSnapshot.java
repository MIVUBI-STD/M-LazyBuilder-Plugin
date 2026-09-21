package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * Read-only visible terrain state for secondary first-party passes.
 *
 * Production storage is double-buffered structure-of-arrays. Entry objects exist
 * only as a lazy compatibility/debug view.
 */
public final class TerrainVisibleDrawSnapshot {
    private static volatile Snapshot current = Snapshot.EMPTY;

    private TerrainVisibleDrawSnapshot() {
    }

    /**
     * Standalone builder factory retained for tests. Runtime code should keep one
     * Builder and reuse it across visibility rebuilds.
     */
    public static Builder builder(int expectedEntries) {
        Builder builder = new Builder();
        builder.reset(expectedEntries);
        return builder;
    }

    public static void publish(Snapshot next) {
        if (next == null) next = Snapshot.EMPTY;

        Snapshot previous = current;
        boolean unchanged = previous.contentEquals(next);
        long revision = unchanged ? previous.revision() : previous.revision() + 1L;

        // Always publish the newly built buffer even when contents are unchanged.
        // This keeps the builder's double-buffer rotation from ever overwriting
        // arrays still referenced by the active snapshot.
        current = next.withRevision(revision);
    }

    public static Snapshot current() {
        return current;
    }

    public static void clear() {
        current = Snapshot.EMPTY;
    }

    /** Compatibility/debug projection only. */
    public record Entry(
            VertexBuffer buffer,
            int layerSlot,
            int originX,
            int originY,
            int originZ
    ) {
        public boolean usable() {
            return buffer != null && !buffer.isClosed() && layerSlot >= 0 && layerSlot < 5;
        }
    }

    public static final class Snapshot {
        private static final VertexBuffer[] EMPTY_BUFFERS = new VertexBuffer[0];
        private static final byte[] EMPTY_LAYERS = new byte[0];
        private static final int[] EMPTY_INTS = new int[0];
        private static final Snapshot EMPTY = new Snapshot(
                0L,
                EMPTY_BUFFERS,
                EMPTY_LAYERS,
                EMPTY_INTS,
                EMPTY_INTS,
                EMPTY_INTS,
                0,
                0xcbf29ce484222325L
        );

        private final long revision;
        private final VertexBuffer[] buffers;
        private final byte[] layerSlots;
        private final int[] originX;
        private final int[] originY;
        private final int[] originZ;
        private final int count;
        private final long fingerprint;
        private List<Entry> compatibilityView;

        private Snapshot(
                long revision,
                VertexBuffer[] buffers,
                byte[] layerSlots,
                int[] originX,
                int[] originY,
                int[] originZ,
                int count,
                long fingerprint
        ) {
            this.revision = revision;
            this.buffers = buffers;
            this.layerSlots = layerSlots;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.count = Math.max(0, count);
            this.fingerprint = fingerprint;
        }

        public long revision() {
            return revision;
        }

        public int size() {
            return count;
        }

        public boolean isEmpty() {
            return count == 0;
        }

        public VertexBuffer buffer(int index) {
            check(index);
            return buffers[index];
        }

        public int layerSlot(int index) {
            check(index);
            return layerSlots[index];
        }

        public int originX(int index) {
            check(index);
            return originX[index];
        }

        public int originY(int index) {
            check(index);
            return originY[index];
        }

        public int originZ(int index) {
            check(index);
            return originZ[index];
        }

        public boolean usable(int index) {
            check(index);
            VertexBuffer buffer = buffers[index];
            int layer = layerSlots[index];
            return buffer != null && !buffer.isClosed() && layer >= 0 && layer < 5;
        }

        public List<Entry> entries() {
            List<Entry> view = compatibilityView;
            if (view != null) return view;

            view = new AbstractList<>() {
                @Override
                public Entry get(int index) {
                    check(index);
                    return new Entry(
                            buffers[index],
                            layerSlots[index],
                            originX[index],
                            originY[index],
                            originZ[index]
                    );
                }

                @Override
                public int size() {
                    return count;
                }
            };
            compatibilityView = view;
            return view;
        }

        private Snapshot withRevision(long nextRevision) {
            if (nextRevision == revision) return this;
            return new Snapshot(
                    nextRevision,
                    buffers,
                    layerSlots,
                    originX,
                    originY,
                    originZ,
                    count,
                    fingerprint
            );
        }

        private boolean contentEquals(Snapshot other) {
            if (other == null || count != other.count || fingerprint != other.fingerprint) {
                return false;
            }
            for (int index = 0; index < count; index++) {
                if (buffers[index] != other.buffers[index]
                        || layerSlots[index] != other.layerSlots[index]
                        || originX[index] != other.originX[index]
                        || originY[index] != other.originY[index]
                        || originZ[index] != other.originZ[index]) {
                    return false;
                }
            }
            return true;
        }

        private void check(int index) {
            if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        }
    }

    public static final class Builder {
        private final Buffer[] buffers = {new Buffer(), new Buffer()};
        private int nextBuffer;
        private Buffer buffer;

        public Builder() {
        }

        public Builder reset(int expectedEntries) {
            buffer = buffers[nextBuffer];
            nextBuffer = (nextBuffer + 1) & 1;
            buffer.reset(Math.max(0, expectedEntries));
            return this;
        }

        /** Compatibility no-op: revision ownership belongs to publish(). */
        public Builder revision(long ignored) {
            return this;
        }

        public void add(
                VertexBuffer vertexBuffer,
                int layerSlot,
                int originX,
                int originY,
                int originZ
        ) {
            if (buffer == null || vertexBuffer == null || vertexBuffer.isClosed()
                    || layerSlot < 0 || layerSlot >= 5) {
                return;
            }
            buffer.append(vertexBuffer, layerSlot, originX, originY, originZ);
        }

        public Snapshot finish() {
            if (buffer == null || buffer.count == 0) return Snapshot.EMPTY;
            return new Snapshot(
                    0L,
                    buffer.vertexBuffers,
                    buffer.layerSlots,
                    buffer.originX,
                    buffer.originY,
                    buffer.originZ,
                    buffer.count,
                    buffer.fingerprint
            );
        }
    }

    private static final class Buffer {
        private VertexBuffer[] vertexBuffers = new VertexBuffer[0];
        private byte[] layerSlots = new byte[0];
        private int[] originX = new int[0];
        private int[] originY = new int[0];
        private int[] originZ = new int[0];
        private int count;
        private long fingerprint;

        void reset(int expectedEntries) {
            count = 0;
            fingerprint = 0xcbf29ce484222325L;
            ensureCapacity(expectedEntries);
        }

        void append(
                VertexBuffer buffer,
                int layerSlot,
                int x,
                int y,
                int z
        ) {
            ensureCapacity(count + 1);
            int index = count++;
            vertexBuffers[index] = buffer;
            layerSlots[index] = (byte) layerSlot;
            originX[index] = x;
            originY[index] = y;
            originZ[index] = z;

            fingerprint ^= System.identityHashCode(buffer);
            fingerprint *= 0x100000001b3L;
            fingerprint ^= layerSlot;
            fingerprint *= 0x100000001b3L;
            fingerprint ^= x;
            fingerprint *= 0x100000001b3L;
            fingerprint ^= y;
            fingerprint *= 0x100000001b3L;
            fingerprint ^= z;
            fingerprint *= 0x100000001b3L;
        }

        private void ensureCapacity(int required) {
            if (required <= vertexBuffers.length) return;
            int capacity = Math.max(32, vertexBuffers.length);
            while (capacity < required) {
                int next = capacity << 1;
                if (next <= capacity) {
                    capacity = required;
                    break;
                }
                capacity = next;
            }
            vertexBuffers = Arrays.copyOf(vertexBuffers, capacity);
            layerSlots = Arrays.copyOf(layerSlots, capacity);
            originX = Arrays.copyOf(originX, capacity);
            originY = Arrays.copyOf(originY, capacity);
            originZ = Arrays.copyOf(originZ, capacity);
        }
    }
}
