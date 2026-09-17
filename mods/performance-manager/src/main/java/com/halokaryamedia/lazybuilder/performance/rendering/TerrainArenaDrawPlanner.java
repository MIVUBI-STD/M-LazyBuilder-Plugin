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

        int eligible = 0;
        int fallback = 0;
        int batches = 0;
        long bindReductions = 0L;
        Command previous = null;
        int currentBatchSize = 0;

        for (Command command : commands) {
            if (!eligible(command, expectedLayerSlot)) {
                fallback++;
                if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
                currentBatchSize = 0;
                previous = null;
                continue;
            }

            eligible++;
            if (previous == null || !sameBatch(previous, command)) {
                if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
                batches++;
                currentBatchSize = 1;
            } else {
                currentBatchSize++;
            }
            previous = command;
        }

        if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
        return new Plan(eligible, fallback, batches, bindReductions);
    }

    public static Plan combine(Plan left, Plan right) {
        if (left == null) left = Plan.EMPTY;
        if (right == null) right = Plan.EMPTY;
        return new Plan(
                left.eligibleCommands + right.eligibleCommands,
                left.fallbackCommands + right.fallbackCommands,
                left.arenaBatches + right.arenaBatches,
                left.potentialBindReductions + right.potentialBindReductions
        );
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

        long indexOffset = command.indexByteOffset();
        int indexSize = state.indexType().size;
        return indexSize > 0 && indexOffset % indexSize == 0L;
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
    }

    public record Plan(
            int eligibleCommands,
            int fallbackCommands,
            int arenaBatches,
            long potentialBindReductions
    ) {
        public static final Plan EMPTY = new Plan(0, 0, 0, 0L);
    }
}
