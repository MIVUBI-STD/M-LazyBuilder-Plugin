package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Per-layer stream of terrain translations in exact vanilla draw order.
 *
 * Production storage is double-buffered structure-of-arrays. Builders reuse their
 * backing arrays after warm-up; Command objects are materialized only through the
 * compatibility/debug view.
 */
public final class TerrainDrawTransformStream {
    private static final int LAYER_COUNT = 5;
    private static final int TRANSFORM_BYTES = Float.BYTES * 4;
    private static final LayerSnapshot[] EMPTY_LAYERS = emptyLayers();
    private static final AtomicReferenceArray<LayerSnapshot> current =
            new AtomicReferenceArray<>(EMPTY_LAYERS);

    private TerrainDrawTransformStream() {
    }

    public static LayerSnapshot build(
            int layerSlot,
            List<Input> inputs,
            double cameraX,
            double cameraY,
            double cameraZ,
            boolean reverseOrder
    ) {
        if (layerSlot < 0 || layerSlot >= LAYER_COUNT || inputs == null || inputs.isEmpty()) {
            return LayerSnapshot.empty(layerSlot, reverseOrder);
        }

        Builder builder = new Builder();
        builder.reset(layerSlot, cameraX, cameraY, cameraZ, reverseOrder, inputs.size());
        if (reverseOrder) {
            for (int index = inputs.size() - 1; index >= 0; index--) {
                Input input = inputs.get(index);
                if (input != null) {
                    builder.accept(
                            input.source(),
                            input.arenaCommand(),
                            input.originX(),
                            input.originY(),
                            input.originZ()
                    );
                }
            }
        } else {
            for (Input input : inputs) {
                if (input != null) {
                    builder.accept(
                            input.source(),
                            input.arenaCommand(),
                            input.originX(),
                            input.originY(),
                            input.originZ()
                    );
                }
            }
        }
        return builder.finish();
    }

    public static final class Builder {
        private final Buffer[] buffers = {new Buffer(), new Buffer()};
        private int nextBuffer;
        private Buffer buffer;
        private int layerSlot = -1;
        private boolean reverseOrder;
        private double cameraX;
        private double cameraY;
        private double cameraZ;
        private int physicalReady;
        private int candidateRuns;
        private long potentialDrawReduction;
        private int currentRunSize;
        private TerrainArenaDrawPlanner.Command previousReadyArena;

        public void reset(
                int layerSlot,
                double cameraX,
                double cameraY,
                double cameraZ,
                boolean reverseOrder,
                int expectedCommands
        ) {
            this.layerSlot = layerSlot;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.reverseOrder = reverseOrder;
            this.physicalReady = 0;
            this.candidateRuns = 0;
            this.potentialDrawReduction = 0L;
            this.currentRunSize = 0;
            this.previousReadyArena = null;

            buffer = buffers[nextBuffer];
            nextBuffer = (nextBuffer + 1) & 1;
            buffer.reset(expectedCommands);
        }

        public void accept(
                VertexBuffer source,
                TerrainArenaDrawPlanner.Command arenaCommand,
                int originX,
                int originY,
                int originZ
        ) {
            if (buffer == null) return;
            int index = buffer.append(
                    source,
                    arenaCommand,
                    (float) ((double) originX - cameraX),
                    (float) ((double) originY - cameraY),
                    (float) ((double) originZ - cameraZ)
            );

            if (!physicalReady(arenaCommand)) {
                potentialDrawReduction += completedRunReduction(currentRunSize);
                currentRunSize = 0;
                previousReadyArena = null;
                return;
            }

            physicalReady++;
            if (previousReadyArena == null
                    || !sameFutureMultiDrawState(previousReadyArena, arenaCommand)) {
                potentialDrawReduction += completedRunReduction(currentRunSize);
                candidateRuns++;
                currentRunSize = 1;
            } else {
                currentRunSize++;
            }
            previousReadyArena = buffer.arenas[index];
        }

        public LayerSnapshot finish() {
            if (buffer == null || layerSlot < 0 || layerSlot >= LAYER_COUNT || buffer.count == 0) {
                return LayerSnapshot.empty(layerSlot, reverseOrder);
            }

            potentialDrawReduction += completedRunReduction(currentRunSize);
            currentRunSize = 0;
            previousReadyArena = null;

            return new LayerSnapshot(
                    layerSlot,
                    reverseOrder,
                    buffer.sources,
                    buffer.arenas,
                    buffer.offsetX,
                    buffer.offsetY,
                    buffer.offsetZ,
                    buffer.count,
                    physicalReady,
                    Math.max(0, buffer.count - physicalReady),
                    candidateRuns,
                    potentialDrawReduction,
                    (long) buffer.count * TRANSFORM_BYTES
            );
        }
    }

    private static final class Buffer {
        private VertexBuffer[] sources = new VertexBuffer[0];
        private TerrainArenaDrawPlanner.Command[] arenas = new TerrainArenaDrawPlanner.Command[0];
        private float[] offsetX = new float[0];
        private float[] offsetY = new float[0];
        private float[] offsetZ = new float[0];
        private int count;

        void reset(int expected) {
            count = 0;
            ensureCapacity(Math.max(0, expected));
        }

        int append(
                VertexBuffer source,
                TerrainArenaDrawPlanner.Command arena,
                float x,
                float y,
                float z
        ) {
            ensureCapacity(count + 1);
            int index = count++;
            sources[index] = source;
            arenas[index] = arena;
            offsetX[index] = x;
            offsetY[index] = y;
            offsetZ[index] = z;
            return index;
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
            sources = Arrays.copyOf(sources, capacity);
            arenas = Arrays.copyOf(arenas, capacity);
            offsetX = Arrays.copyOf(offsetX, capacity);
            offsetY = Arrays.copyOf(offsetY, capacity);
            offsetZ = Arrays.copyOf(offsetZ, capacity);
        }
    }

    public static synchronized void publish(LayerSnapshot snapshot) {
        if (snapshot == null || snapshot.layerSlot() < 0 || snapshot.layerSlot() >= LAYER_COUNT) return;
        current.set(snapshot.layerSlot(), snapshot);
        TerrainMultiDrawCommandStream.publish(snapshot);
    }

    public static LayerSnapshot layer(int layerSlot) {
        if (layerSlot < 0 || layerSlot >= LAYER_COUNT) return LayerSnapshot.empty(layerSlot, false);
        LayerSnapshot snapshot = current.get(layerSlot);
        return snapshot == null ? LayerSnapshot.empty(layerSlot, false) : snapshot;
    }

    public static synchronized void clearLayer(int layerSlot) {
        if (layerSlot < 0 || layerSlot >= LAYER_COUNT) return;
        LayerSnapshot existing = current.get(layerSlot);
        if (existing != null && existing.commandCount() == 0) {
            TerrainMultiDrawCommandStream.clearLayer(layerSlot);
            return;
        }
        current.set(layerSlot, EMPTY_LAYERS[layerSlot]);
        TerrainMultiDrawCommandStream.clearLayer(layerSlot);
    }

    public static Snapshot snapshot() {
        int commands = 0;
        int physicalReady = 0;
        int transformBlocked = 0;
        int candidateRuns = 0;
        long potentialDrawReduction = 0L;
        long packedBytes = 0L;

        for (int layerIndex = 0; layerIndex < LAYER_COUNT; layerIndex++) {
            LayerSnapshot layer = current.get(layerIndex);
            if (layer == null) continue;
            commands += layer.commandCount();
            physicalReady += layer.physicalReadyCommands();
            transformBlocked += layer.transformBlockedCommands();
            candidateRuns += layer.multiDrawCandidateRuns();
            potentialDrawReduction += layer.potentialDrawCallReduction();
            packedBytes += layer.packedTransformBytes();
        }

        return new Snapshot(
                commands,
                physicalReady,
                transformBlocked,
                candidateRuns,
                potentialDrawReduction,
                packedBytes
        );
    }

    public static synchronized void clear() {
        for (int index = 0; index < LAYER_COUNT; index++) {
            current.set(index, EMPTY_LAYERS[index]);
        }
        TerrainMultiDrawCommandStream.clear();
    }

    private static boolean physicalReady(TerrainArenaDrawPlanner.Command arena) {
        return TerrainPhysicalArenaPolicy.isDrawReady(arena);
    }

    private static boolean sameFutureMultiDrawState(
            TerrainArenaDrawPlanner.Command leftCommand,
            TerrainArenaDrawPlanner.Command rightCommand
    ) {
        if (leftCommand == null || rightCommand == null
                || leftCommand.handle() == null || rightCommand.handle() == null
                || leftCommand.state() == null || rightCommand.state() == null) {
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState leftState = leftCommand.state();
        TerrainArenaDrawStateRegistry.DrawState rightState = rightCommand.state();
        return leftCommand.handle().arenaKey().equals(rightCommand.handle().arenaKey())
                && leftState.format() == rightState.format()
                && leftState.mode() == rightState.mode()
                && leftState.indexType() == rightState.indexType()
                && (leftState.indexPayloadBytes() > 0) == (rightState.indexPayloadBytes() > 0);
    }

    private static long completedRunReduction(int runSize) {
        return runSize > 1 ? runSize - 1L : 0L;
    }

    private static LayerSnapshot[] emptyLayers() {
        LayerSnapshot[] layers = new LayerSnapshot[LAYER_COUNT];
        for (int i = 0; i < layers.length; i++) layers[i] = LayerSnapshot.empty(i, i == 3);
        return layers;
    }

    public record Input(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            int originX,
            int originY,
            int originZ
    ) {
        public Input(TerrainArenaDrawPlanner.Command arenaCommand, int originX, int originY, int originZ) {
            this(null, arenaCommand, originX, originY, originZ);
        }
    }

    /** Compatibility/debug projection only. */
    public record Command(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            float modelOffsetX,
            float modelOffsetY,
            float modelOffsetZ
    ) {
        public boolean physicalReady() {
            return TerrainPhysicalArenaPolicy.isDrawReady(arenaCommand);
        }
    }

    public static final class LayerSnapshot {
        private static final VertexBuffer[] EMPTY_SOURCES = new VertexBuffer[0];
        private static final TerrainArenaDrawPlanner.Command[] EMPTY_ARENAS =
                new TerrainArenaDrawPlanner.Command[0];
        private static final float[] EMPTY_FLOATS = new float[0];

        private final int layerSlot;
        private final boolean reverseOrder;
        private final VertexBuffer[] sources;
        private final TerrainArenaDrawPlanner.Command[] arenas;
        private final float[] offsetX;
        private final float[] offsetY;
        private final float[] offsetZ;
        private final int commandCount;
        private final int physicalReadyCommands;
        private final int transformBlockedCommands;
        private final int multiDrawCandidateRuns;
        private final long potentialDrawCallReduction;
        private final long packedTransformBytes;
        private List<Command> compatibilityView;

        private LayerSnapshot(
                int layerSlot,
                boolean reverseOrder,
                VertexBuffer[] sources,
                TerrainArenaDrawPlanner.Command[] arenas,
                float[] offsetX,
                float[] offsetY,
                float[] offsetZ,
                int commandCount,
                int physicalReadyCommands,
                int transformBlockedCommands,
                int multiDrawCandidateRuns,
                long potentialDrawCallReduction,
                long packedTransformBytes
        ) {
            this.layerSlot = layerSlot;
            this.reverseOrder = reverseOrder;
            this.sources = sources;
            this.arenas = arenas;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.commandCount = Math.max(0, commandCount);
            this.physicalReadyCommands = physicalReadyCommands;
            this.transformBlockedCommands = transformBlockedCommands;
            this.multiDrawCandidateRuns = multiDrawCandidateRuns;
            this.potentialDrawCallReduction = potentialDrawCallReduction;
            this.packedTransformBytes = packedTransformBytes;
        }

        public int layerSlot() {
            return layerSlot;
        }

        public boolean reverseOrder() {
            return reverseOrder;
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

        public float modelOffsetX(int index) {
            check(index);
            return offsetX[index];
        }

        public float modelOffsetY(int index) {
            check(index);
            return offsetY[index];
        }

        public float modelOffsetZ(int index) {
            check(index);
            return offsetZ[index];
        }

        public int physicalReadyCommands() {
            return physicalReadyCommands;
        }

        public int transformBlockedCommands() {
            return transformBlockedCommands;
        }

        public int multiDrawCandidateRuns() {
            return multiDrawCandidateRuns;
        }

        public long potentialDrawCallReduction() {
            return potentialDrawCallReduction;
        }

        public long packedTransformBytes() {
            return packedTransformBytes;
        }

        public List<Command> commands() {
            List<Command> view = compatibilityView;
            if (view != null) return view;
            view = new AbstractList<>() {
                @Override
                public Command get(int index) {
                    check(index);
                    return new Command(
                            sources[index],
                            arenas[index],
                            offsetX[index],
                            offsetY[index],
                            offsetZ[index]
                    );
                }

                @Override
                public int size() {
                    return commandCount;
                }
            };
            compatibilityView = view;
            return view;
        }

        private void check(int index) {
            if (index < 0 || index >= commandCount) {
                throw new IndexOutOfBoundsException(index);
            }
        }

        private static LayerSnapshot empty(int layerSlot, boolean reverseOrder) {
            return new LayerSnapshot(
                    layerSlot,
                    reverseOrder,
                    EMPTY_SOURCES,
                    EMPTY_ARENAS,
                    EMPTY_FLOATS,
                    EMPTY_FLOATS,
                    EMPTY_FLOATS,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L
            );
        }
    }

    public record Snapshot(
            int commands,
            int physicalReadyCommands,
            int transformBlockedCommands,
            int multiDrawCandidateRuns,
            long potentialDrawCallReduction,
            long packedTransformBytes
    ) {
    }
}
