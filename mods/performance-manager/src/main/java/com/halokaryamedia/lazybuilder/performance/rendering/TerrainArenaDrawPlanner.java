package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;

import java.util.List;

/**
 * Builds a draw-order-preserving plan over live arena handles plus captured vanilla draw state.
 *
 * Future shared-buffer draws may batch only consecutive commands with the same arena and compatible
 * format/mode/index state. Any incomplete command remains an explicit vanilla fallback boundary.
 */
public final class TerrainArenaDrawPlanner {
    private TerrainArenaDrawPlanner() {
    }

    public static Plan plan(List<Command> commands, int expectedLayerSlot) {
        if (commands == null || commands.isEmpty()) return Plan.EMPTY;
        Accumulator accumulator = new Accumulator(expectedLayerSlot);
        for (Command command : commands) accumulator.accept(command);
        return accumulator.finish();
    }

    public static Accumulator accumulator(int expectedLayerSlot) {
        return new Accumulator(expectedLayerSlot);
    }

    public static final class Accumulator {
        private final int expectedLayerSlot;
        private int eligible;
        private int fallback;
        private int batches;
        private long bindReductions;
        private Command previous;
        private int currentBatchSize;
        private int baseVertexReady;
        private int baseVertexBatches;
        private long baseVertexBindReductions;
        private Command previousBaseVertex;
        private int currentBaseVertexBatchSize;

        private Accumulator(int expectedLayerSlot) {
            this.expectedLayerSlot = expectedLayerSlot;
        }

        public void accept(Command command) {
            if (!eligible(command, expectedLayerSlot)) {
                fallback++;
                flushBatches();
                previous = null;
                previousBaseVertex = null;
                return;
            }

            eligible++;
            if (previous == null || !sameBatch(previous, command)) {
                bindReductions += completedBatchReduction(currentBatchSize);
                batches++;
                currentBatchSize = 1;
            } else {
                currentBatchSize++;
            }
            previous = command;

            if (!TerrainArenaBaseVertexPolicy.isReady(command)) {
                baseVertexBindReductions += completedBatchReduction(currentBaseVertexBatchSize);
                currentBaseVertexBatchSize = 0;
                previousBaseVertex = null;
                return;
            }

            baseVertexReady++;
            if (previousBaseVertex == null || !sameBatch(previousBaseVertex, command)) {
                baseVertexBindReductions += completedBatchReduction(currentBaseVertexBatchSize);
                baseVertexBatches++;
                currentBaseVertexBatchSize = 1;
            } else {
                currentBaseVertexBatchSize++;
            }
            previousBaseVertex = command;
        }

        public Plan finish() {
            bindReductions += completedBatchReduction(currentBatchSize);
            baseVertexBindReductions += completedBatchReduction(currentBaseVertexBatchSize);
            currentBatchSize = 0;
            currentBaseVertexBatchSize = 0;
            return new Plan(
                    eligible,
                    fallback,
                    batches,
                    bindReductions,
                    baseVertexReady,
                    baseVertexBatches,
                    baseVertexBindReductions
            );
        }

        private void flushBatches() {
            bindReductions += completedBatchReduction(currentBatchSize);
            baseVertexBindReductions += completedBatchReduction(currentBaseVertexBatchSize);
            currentBatchSize = 0;
            currentBaseVertexBatchSize = 0;
        }
    }

    public static Plan combine(Plan left, Plan right) {
        if (left == null) left = Plan.EMPTY;
        if (right == null) right = Plan.EMPTY;
        return new Plan(
                left.eligibleCommands + right.eligibleCommands,
                left.fallbackCommands + right.fallbackCommands,
                left.arenaBatches + right.arenaBatches,
                left.potentialBindReductions + right.potentialBindReductions,
                left.baseVertexReadyCommands + right.baseVertexReadyCommands,
                left.baseVertexBatches + right.baseVertexBatches,
                left.potentialBaseVertexBindReductions + right.potentialBaseVertexBindReductions
        );
    }

    private static long completedBatchReduction(int batchSize) {
        return batchSize > 1 ? batchSize - 1L : 0L;
    }

    private static boolean eligible(Command command, int expectedLayerSlot) {
        if (command == null || command.handle == null || command.state == null) return false;
        TerrainRegionAllocationRegistry.Handle handle = command.handle;
        TerrainArenaDrawStateRegistry.DrawState state = command.state;
        if (handle.arenaKey() == null || handle.arenaKey().layerSlot() != expectedLayerSlot) return false;
        if (!state.drawable()) return false;

        long required = state.requiredAllocationBytes();
        if (required <= 0L || handle.payloadBytes() < required || handle.sizeBytes() < required) return false;
        if (handle.offsetBytes() < 0L || handle.offsetBytes() > Long.MAX_VALUE - required) return false;

        if (state.indexPayloadBytes() == 0) {
            return true;
        }

        long indexOffset = command.indexByteOffset();
        int indexSize = state.indexType().size;
        return indexSize > 0 && indexOffset >= 0L && indexOffset % indexSize == 0L;
    }

    private static boolean sameBatch(Command left, Command right) {
        TerrainArenaDrawStateRegistry.DrawState leftState = left.state;
        TerrainArenaDrawStateRegistry.DrawState rightState = right.state;
        return left.handle.arenaKey().equals(right.handle.arenaKey())
                && leftState.format() == rightState.format()
                && leftState.mode() == rightState.mode()
                && leftState.indexType() == rightState.indexType();
    }

    public record Command(
            TerrainRegionAllocationRegistry.Handle handle,
            TerrainArenaDrawStateRegistry.DrawState state
    ) {
        public long vertexByteOffset() {
            return handle == null ? -1L : handle.offsetBytes();
        }

        public long indexByteOffset() {
            if (handle == null || state == null) return -1L;
            long inner = state.indexOffsetWithinAllocation();
            return handle.offsetBytes() > Long.MAX_VALUE - inner ? -1L : handle.offsetBytes() + inner;
        }

        public int vertexStrideBytes() {
            VertexFormat format = state == null ? null : state.format();
            return format == null ? 0 : format.getVertexSizeByte();
        }

        public int vertexCount() {
            return state == null ? 0 : state.vertexCount();
        }

        public int indexCount() {
            return state == null ? 0 : state.indexCount();
        }

        public int baseVertex() {
            return TerrainArenaBaseVertexPolicy.baseVertex(this);
        }
    }

    public record Plan(
            int eligibleCommands,
            int fallbackCommands,
            int arenaBatches,
            long potentialBindReductions,
            int baseVertexReadyCommands,
            int baseVertexBatches,
            long potentialBaseVertexBindReductions
    ) {
        public static final Plan EMPTY = new Plan(0, 0, 0, 0L, 0, 0, 0L);
    }
}
