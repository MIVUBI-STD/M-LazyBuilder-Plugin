package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.List;

/**
 * Packs physical-ready terrain draws into GPU-ready command and transform payloads.
 *
 * Production storage is structure-of-arrays to avoid one PackedCommand allocation
 * per visible section. The PackedCommand view is retained only for tests/debug callers.
 */
public final class TerrainMultiDrawCommandStream {
    private static final int LAYER_COUNT = 5;
    private static final int COMMAND_BYTES = 24;
    private static final int TRANSFORM_BYTES = 16;
    private static final ByteBuffer EMPTY =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asReadOnlyBuffer();
    private static ByteBuffer transformScratch =
            ByteBuffer.allocateDirect(TRANSFORM_BYTES).order(ByteOrder.nativeOrder());
    private static final LayerPacket[] EMPTY_LAYERS = emptyLayers();
    private static final PacketBuilder[] PACKET_BUILDERS = packetBuilders();
    private static volatile LayerPacket[] current = EMPTY_LAYERS.clone();

    private TerrainMultiDrawCommandStream() {
    }

    public static LayerPacket build(TerrainDrawTransformStream.LayerSnapshot layer) {
        return new PacketBuilder().build(layer);
    }

    public static synchronized void publish(TerrainDrawTransformStream.LayerSnapshot layer) {
        if (layer == null || layer.layerSlot() < 0 || layer.layerSlot() >= LAYER_COUNT) return;
        LayerPacket packet = PACKET_BUILDERS[layer.layerSlot()].build(layer);
        LayerPacket[] next = current.clone();
        next[packet.layerSlot()] = packet;
        current = next;
    }

    public static LayerPacket layer(int layerSlot) {
        LayerPacket[] snapshot = current;
        if (layerSlot < 0 || layerSlot >= snapshot.length) return LayerPacket.empty(layerSlot);
        return snapshot[layerSlot];
    }

    public static synchronized void clearLayer(int layerSlot) {
        if (layerSlot < 0 || layerSlot >= LAYER_COUNT) return;
        LayerPacket existing = current[layerSlot];
        if (existing != null && existing.commandCount() == 0) return;
        LayerPacket[] next = current.clone();
        next[layerSlot] = EMPTY_LAYERS[layerSlot];
        current = next;
    }

    public static Snapshot snapshot() {
        int commands = 0;
        int runs = 0;
        long reductions = 0L;
        long commandBytes = 0L;
        long transformBytes = 0L;
        for (LayerPacket layer : current) {
            commands += layer.commandCount();
            runs += layer.candidateRuns();
            reductions += layer.potentialDrawCallReduction();
            commandBytes += layer.packedCommandBytes();
            transformBytes += layer.packedTransformBytes();
        }
        return new Snapshot(commands, runs, reductions, commandBytes, transformBytes);
    }

    public static synchronized ByteBuffer packTransforms(LayerPacket packet) {
        if (packet == null || packet.commandCount() == 0) return emptyBuffer();
        int required = Math.multiplyExact(packet.commandCount(), TRANSFORM_BYTES);
        transformScratch = ensureCapacity(transformScratch, required);
        transformScratch.clear();
        for (int index = 0; index < packet.commandCount(); index++) {
            transformScratch.putFloat(packet.modelOffsetX(index));
            transformScratch.putFloat(packet.modelOffsetY(index));
            transformScratch.putFloat(packet.modelOffsetZ(index));
            transformScratch.putFloat(0.0F);
        }
        transformScratch.flip();
        return transformScratch.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public static synchronized void clear() {
        current = EMPTY_LAYERS.clone();
    }

    private static ByteBuffer emptyBuffer() {
        return EMPTY.duplicate().order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer ensureCapacity(ByteBuffer current, int required) {
        if (required <= current.capacity()) return current;
        int capacity = Math.max(current.capacity(), 1);
        while (capacity < required) {
            int next = capacity << 1;
            if (next <= 0 || next < capacity) {
                capacity = required;
                break;
            }
            capacity = next;
        }
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    private static LayerPacket[] emptyLayers() {
        LayerPacket[] layers = new LayerPacket[LAYER_COUNT];
        for (int i = 0; i < layers.length; i++) layers[i] = LayerPacket.empty(i);
        return layers;
    }

    private static PacketBuilder[] packetBuilders() {
        PacketBuilder[] builders = new PacketBuilder[LAYER_COUNT];
        for (int index = 0; index < builders.length; index++) {
            builders[index] = new PacketBuilder();
        }
        return builders;
    }

    private static final class PacketBuilder {
        private final PacketBuffer[] buffers = {new PacketBuffer(), new PacketBuffer()};
        private int nextBuffer;

        LayerPacket build(TerrainDrawTransformStream.LayerSnapshot layer) {
            if (layer == null || layer.layerSlot() < 0 || layer.layerSlot() >= LAYER_COUNT) {
                return LayerPacket.empty(layer == null ? -1 : layer.layerSlot());
            }

            PacketBuffer buffer = buffers[nextBuffer];
            nextBuffer = (nextBuffer + 1) & 1;
            buffer.reset(Math.max(0, layer.physicalReadyCommands()));

            for (int orderIndex = 0; orderIndex < layer.commandCount(); orderIndex++) {
                TerrainArenaDrawPlanner.Command arena = layer.arenaCommand(orderIndex);
                if (!TerrainPhysicalArenaPolicy.isDrawReady(arena)) continue;

                TerrainArenaDrawStateRegistry.DrawState state = arena == null ? null : arena.state();
                if (arena == null || state == null) continue;

                int baseVertex = TerrainPhysicalArenaPolicy.baseVertex(arena);
                if (baseVertex < 0) continue;

                long indexOffset = state.indexPayloadBytes() > 0 ? arena.indexByteOffset() : 0L;
                if (indexOffset < 0L) continue;

                buffer.append(
                        layer.source(orderIndex),
                        arena,
                        state.indexCount(),
                        indexOffset,
                        baseVertex,
                        orderIndex,
                        layer.modelOffsetX(orderIndex),
                        layer.modelOffsetY(orderIndex),
                        layer.modelOffsetZ(orderIndex)
                );
            }

            int count = buffer.count;
            return new LayerPacket(
                    layer.layerSlot(),
                    buffer.sources,
                    buffer.arenas,
                    buffer.indexCounts,
                    buffer.indexByteOffsets,
                    buffer.baseVertices,
                    buffer.transformIndices,
                    buffer.orderIndices,
                    buffer.offsetX,
                    buffer.offsetY,
                    buffer.offsetZ,
                    count,
                    layer.multiDrawCandidateRuns(),
                    layer.potentialDrawCallReduction(),
                    (long) count * COMMAND_BYTES,
                    (long) count * TRANSFORM_BYTES
            );
        }
    }

    private static final class PacketBuffer {
        private VertexBuffer[] sources = new VertexBuffer[0];
        private TerrainArenaDrawPlanner.Command[] arenas =
                new TerrainArenaDrawPlanner.Command[0];
        private int[] indexCounts = new int[0];
        private long[] indexByteOffsets = new long[0];
        private int[] baseVertices = new int[0];
        private int[] transformIndices = new int[0];
        private int[] orderIndices = new int[0];
        private float[] offsetX = new float[0];
        private float[] offsetY = new float[0];
        private float[] offsetZ = new float[0];
        private int count;

        void reset(int expected) {
            count = 0;
            ensureCapacity(expected);
        }

        void append(
                VertexBuffer source,
                TerrainArenaDrawPlanner.Command arena,
                int indexCount,
                long indexByteOffset,
                int baseVertex,
                int orderIndex,
                float x,
                float y,
                float z
        ) {
            ensureCapacity(count + 1);
            int index = count++;
            sources[index] = source;
            arenas[index] = arena;
            indexCounts[index] = indexCount;
            indexByteOffsets[index] = indexByteOffset;
            baseVertices[index] = baseVertex;
            transformIndices[index] = index;
            orderIndices[index] = orderIndex;
            offsetX[index] = x;
            offsetY[index] = y;
            offsetZ[index] = z;
        }

        private void ensureCapacity(int required) {
            if (required <= sources.length) return;
            int capacity = Math.max(16, sources.length);
            while (capacity < required) {
                int next = capacity << 1;
                if (next <= capacity) {
                    capacity = required;
                    break;
                }
                capacity = next;
            }
            sources = java.util.Arrays.copyOf(sources, capacity);
            arenas = java.util.Arrays.copyOf(arenas, capacity);
            indexCounts = java.util.Arrays.copyOf(indexCounts, capacity);
            indexByteOffsets = java.util.Arrays.copyOf(indexByteOffsets, capacity);
            baseVertices = java.util.Arrays.copyOf(baseVertices, capacity);
            transformIndices = java.util.Arrays.copyOf(transformIndices, capacity);
            orderIndices = java.util.Arrays.copyOf(orderIndices, capacity);
            offsetX = java.util.Arrays.copyOf(offsetX, capacity);
            offsetY = java.util.Arrays.copyOf(offsetY, capacity);
            offsetZ = java.util.Arrays.copyOf(offsetZ, capacity);
        }
    }

    /**
     * Compatibility/test projection. Production renderer code should use LayerPacket
     * primitive accessors directly.
     */
    public record PackedCommand(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            int indexCount,
            long indexByteOffset,
            int baseVertex,
            int transformIndex,
            int orderIndex,
            float modelOffsetX,
            float modelOffsetY,
            float modelOffsetZ
    ) {
    }

    public static final class LayerPacket {
        private static final VertexBuffer[] EMPTY_SOURCES = new VertexBuffer[0];
        private static final TerrainArenaDrawPlanner.Command[] EMPTY_ARENAS =
                new TerrainArenaDrawPlanner.Command[0];
        private static final int[] EMPTY_INTS = new int[0];
        private static final long[] EMPTY_LONGS = new long[0];
        private static final float[] EMPTY_FLOATS = new float[0];

        private final int layerSlot;
        private final VertexBuffer[] sources;
        private final TerrainArenaDrawPlanner.Command[] arenas;
        private final int[] indexCounts;
        private final long[] indexByteOffsets;
        private final int[] baseVertices;
        private final int[] transformIndices;
        private final int[] orderIndices;
        private final float[] modelOffsetX;
        private final float[] modelOffsetY;
        private final float[] modelOffsetZ;
        private final int commandCount;
        private final int candidateRuns;
        private final long potentialDrawCallReduction;
        private final long packedCommandBytes;
        private final long packedTransformBytes;
        private List<PackedCommand> compatibilityView;

        LayerPacket(
                int layerSlot,
                VertexBuffer[] sources,
                TerrainArenaDrawPlanner.Command[] arenas,
                int[] indexCounts,
                long[] indexByteOffsets,
                int[] baseVertices,
                int[] transformIndices,
                int[] orderIndices,
                float[] modelOffsetX,
                float[] modelOffsetY,
                float[] modelOffsetZ,
                int commandCount,
                int candidateRuns,
                long potentialDrawCallReduction,
                long packedCommandBytes,
                long packedTransformBytes
        ) {
            this.layerSlot = layerSlot;
            this.sources = sources;
            this.arenas = arenas;
            this.indexCounts = indexCounts;
            this.indexByteOffsets = indexByteOffsets;
            this.baseVertices = baseVertices;
            this.transformIndices = transformIndices;
            this.orderIndices = orderIndices;
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetY = modelOffsetY;
            this.modelOffsetZ = modelOffsetZ;
            this.commandCount = Math.max(0, commandCount);
            this.candidateRuns = candidateRuns;
            this.potentialDrawCallReduction = potentialDrawCallReduction;
            this.packedCommandBytes = packedCommandBytes;
            this.packedTransformBytes = packedTransformBytes;
        }

        /** Compatibility constructor used by focused unit tests. */
        public LayerPacket(
                int layerSlot,
                List<PackedCommand> commands,
                int candidateRuns,
                long potentialDrawCallReduction,
                long packedCommandBytes,
                long packedTransformBytes
        ) {
            this(
                    layerSlot,
                    sources(commands),
                    arenas(commands),
                    ints(commands, Field.INDEX_COUNT),
                    longs(commands),
                    ints(commands, Field.BASE_VERTEX),
                    ints(commands, Field.TRANSFORM_INDEX),
                    ints(commands, Field.ORDER_INDEX),
                    floats(commands, Field.OFFSET_X),
                    floats(commands, Field.OFFSET_Y),
                    floats(commands, Field.OFFSET_Z),
                    commands == null ? 0 : commands.size(),
                    candidateRuns,
                    potentialDrawCallReduction,
                    packedCommandBytes,
                    packedTransformBytes
            );
        }

        public int layerSlot() {
            return layerSlot;
        }

        public int commandCount() {
            return commandCount;
        }

        public VertexBuffer source(int index) {
            check(index);
            return sources[index];
        }

        public TerrainArenaDrawPlanner.Command arenaCommand(int index) {
            check(index);
            return arenas[index];
        }

        public int indexCount(int index) {
            check(index);
            return indexCounts[index];
        }

        public long indexByteOffset(int index) {
            check(index);
            return indexByteOffsets[index];
        }

        public int baseVertex(int index) {
            check(index);
            return baseVertices[index];
        }

        public int transformIndex(int index) {
            check(index);
            return transformIndices[index];
        }

        public int orderIndex(int index) {
            check(index);
            return orderIndices[index];
        }

        public float modelOffsetX(int index) {
            check(index);
            return modelOffsetX[index];
        }

        public float modelOffsetY(int index) {
            check(index);
            return modelOffsetY[index];
        }

        public float modelOffsetZ(int index) {
            check(index);
            return modelOffsetZ[index];
        }

        public int candidateRuns() {
            return candidateRuns;
        }

        public long potentialDrawCallReduction() {
            return potentialDrawCallReduction;
        }

        public long packedCommandBytes() {
            return packedCommandBytes;
        }

        public long packedTransformBytes() {
            return packedTransformBytes;
        }

        public List<PackedCommand> commands() {
            List<PackedCommand> view = compatibilityView;
            if (view != null) return view;
            view = new AbstractList<>() {
                @Override
                public PackedCommand get(int index) {
                    return packedCommand(index);
                }

                @Override
                public int size() {
                    return commandCount;
                }
            };
            compatibilityView = view;
            return view;
        }

        PackedCommand packedCommand(int index) {
            return new PackedCommand(
                    source(index),
                    arenaCommand(index),
                    indexCount(index),
                    indexByteOffset(index),
                    baseVertex(index),
                    transformIndex(index),
                    orderIndex(index),
                    modelOffsetX(index),
                    modelOffsetY(index),
                    modelOffsetZ(index)
            );
        }

        private void check(int index) {
            if (index < 0 || index >= commandCount) {
                throw new IndexOutOfBoundsException(index);
            }
        }

        static LayerPacket empty(int layerSlot) {
            return new LayerPacket(
                    layerSlot,
                    EMPTY_SOURCES,
                    EMPTY_ARENAS,
                    EMPTY_INTS,
                    EMPTY_LONGS,
                    EMPTY_INTS,
                    EMPTY_INTS,
                    EMPTY_INTS,
                    EMPTY_FLOATS,
                    EMPTY_FLOATS,
                    EMPTY_FLOATS,
                    0,
                    0,
                    0L,
                    0L,
                    0L
            );
        }

        private enum Field {
            INDEX_COUNT,
            BASE_VERTEX,
            TRANSFORM_INDEX,
            ORDER_INDEX,
            OFFSET_X,
            OFFSET_Y,
            OFFSET_Z
        }

        private static VertexBuffer[] sources(List<PackedCommand> commands) {
            int size = commands == null ? 0 : commands.size();
            VertexBuffer[] result = new VertexBuffer[size];
            for (int i = 0; i < size; i++) result[i] = commands.get(i).source();
            return result;
        }

        private static TerrainArenaDrawPlanner.Command[] arenas(List<PackedCommand> commands) {
            int size = commands == null ? 0 : commands.size();
            TerrainArenaDrawPlanner.Command[] result = new TerrainArenaDrawPlanner.Command[size];
            for (int i = 0; i < size; i++) result[i] = commands.get(i).arenaCommand();
            return result;
        }

        private static int[] ints(List<PackedCommand> commands, Field field) {
            int size = commands == null ? 0 : commands.size();
            int[] result = new int[size];
            for (int i = 0; i < size; i++) {
                PackedCommand command = commands.get(i);
                result[i] = switch (field) {
                    case INDEX_COUNT -> command.indexCount();
                    case BASE_VERTEX -> command.baseVertex();
                    case TRANSFORM_INDEX -> command.transformIndex();
                    case ORDER_INDEX -> command.orderIndex();
                    default -> throw new IllegalArgumentException("Not an int field: " + field);
                };
            }
            return result;
        }

        private static long[] longs(List<PackedCommand> commands) {
            int size = commands == null ? 0 : commands.size();
            long[] result = new long[size];
            for (int i = 0; i < size; i++) result[i] = commands.get(i).indexByteOffset();
            return result;
        }

        private static float[] floats(List<PackedCommand> commands, Field field) {
            int size = commands == null ? 0 : commands.size();
            float[] result = new float[size];
            for (int i = 0; i < size; i++) {
                PackedCommand command = commands.get(i);
                result[i] = switch (field) {
                    case OFFSET_X -> command.modelOffsetX();
                    case OFFSET_Y -> command.modelOffsetY();
                    case OFFSET_Z -> command.modelOffsetZ();
                    default -> throw new IllegalArgumentException("Not a float field: " + field);
                };
            }
            return result;
        }
    }

    public record Snapshot(
            int commands,
            int candidateRuns,
            long potentialDrawCallReduction,
            long packedCommandBytes,
            long packedTransformBytes
    ) {
    }
}
