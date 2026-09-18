package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainArenaDrawPlannerTest {
    @Test
    void batchesOnlyConsecutiveCommandsWithSameArenaAndDrawState() {
        var arenaA = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var arenaB = new TerrainRegionAllocationRegistry.ArenaKey(1, 0, 0, 0);
        var state = state(400, 64);

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(
                command(arenaA, 0L, state, 1L),
                command(arenaA, 512L, state, 2L),
                command(arenaB, 0L, state, 3L),
                command(arenaB, 512L, state, 4L)
        ), 0);

        assertEquals(4, plan.eligibleCommands());
        assertEquals(0, plan.fallbackCommands());
        assertEquals(2, plan.arenaBatches());
        assertEquals(2L, plan.potentialBindReductions());
        assertEquals(0, plan.baseVertexReadyCommands());
        assertEquals(0, plan.baseVertexBatches());
    }

    @Test
    void fallbackBreaksArenaBatchAndPreservesOrderBoundary() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 2);
        var state = state(256, 48);

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(Arrays.asList(
                command(arena, 0L, state, 1L),
                command(arena, 512L, state, 2L),
                null,
                command(arena, 1024L, state, 3L)
        ), 2);

        assertEquals(3, plan.eligibleCommands());
        assertEquals(1, plan.fallbackCommands());
        assertEquals(2, plan.arenaBatches());
        assertEquals(1L, plan.potentialBindReductions());
    }

    @Test
    void incompatibleFormatBreaksBatchWithoutChangingEligibility() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var terrainState = state(256, 48);
        var otherState = new TerrainArenaDrawStateRegistry.DrawState(
                VertexFormats.POSITION_COLOR,
                8,
                12,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                256,
                48
        );

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(
                command(arena, 0L, terrainState, 1L),
                command(arena, 512L, otherState, 2L)
        ), 0);

        assertEquals(2, plan.eligibleCommands());
        assertEquals(2, plan.arenaBatches());
        assertEquals(0L, plan.potentialBindReductions());
    }

    @Test
    void rejectsWrongLayerMissingStateAndUndersizedAllocation() {
        var expected = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 1);
        var wrongLayer = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 3);
        var state = state(300, 80);
        long required = state.requiredAllocationBytes();

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(
                new TerrainArenaDrawPlanner.Command(handle(expected, 0L, required, required, 1L), null),
                new TerrainArenaDrawPlanner.Command(handle(expected, 512L, required - 1L, required, 2L), state),
                new TerrainArenaDrawPlanner.Command(handle(wrongLayer, 1024L, required, required, 3L), state)
        ), 1);

        assertEquals(0, plan.eligibleCommands());
        assertEquals(3, plan.fallbackCommands());
        assertEquals(0, plan.arenaBatches());
    }

    @Test
    void exposesAlignedVertexAndIndexOffsetsForFuturePhysicalDraw() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var state = state(300, 80);
        var command = command(arena, 1024L, state, 1L);

        assertEquals(1024L, command.vertexByteOffset());
        assertEquals(1536L, command.indexByteOffset());
        assertEquals(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte(), command.vertexStrideBytes());
        assertEquals(16, command.vertexCount());
        assertEquals(24, command.indexCount());
    }

    @Test
    void marksSequentialIndexCommandsReadyForBaseVertexDraw() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var state = sequentialState();
        int stride = state.format().getVertexSizeByte();
        long secondOffset = 256L;
        assertEquals(0L, secondOffset % stride);

        TerrainArenaDrawPlanner.Command first = command(arena, 0L, state, 1L);
        TerrainArenaDrawPlanner.Command second = command(arena, secondOffset, state, 2L);
        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(first, second), 0);

        assertEquals(2, plan.baseVertexReadyCommands());
        assertEquals(1, plan.baseVertexBatches());
        assertEquals(1L, plan.potentialBaseVertexBindReductions());
        assertEquals(0, first.baseVertex());
        assertEquals((int) (secondOffset / stride), second.baseVertex());
    }

    @Test
    void customIndicesOrMisalignedVertexOffsetRemainOutsideBaseVertexSubset() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var sequential = sequentialState();
        var sorted = state(sequential.vertexPayloadBytes(), 64);

        TerrainArenaDrawPlanner.Command misaligned = command(arena, 257L, sequential, 1L);
        TerrainArenaDrawPlanner.Command customIndices = command(arena, 512L, sorted, 2L);
        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(misaligned, customIndices), 0);

        assertEquals(2, plan.eligibleCommands());
        assertEquals(0, plan.baseVertexReadyCommands());
        assertEquals(-1, misaligned.baseVertex());
        assertEquals(-1, customIndices.baseVertex());
    }

    @Test
    void mismatchedVertexPayloadStaysOutsideBaseVertexSubset() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        int expected = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte() * 16;
        var mismatched = state(expected - 1, 0);
        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(
                List.of(command(arena, 0L, mismatched, 1L)),
                0
        );

        assertEquals(1, plan.eligibleCommands());
        assertEquals(0, plan.baseVertexReadyCommands());
    }

    @Test
    void streamingAccumulatorMatchesListPlanner() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var state = sequentialState();
        List<TerrainArenaDrawPlanner.Command> commands = Arrays.asList(
                command(arena, 0L, state, 1L),
                command(arena, 256L, state, 2L),
                null,
                command(arena, 512L, state, 3L)
        );

        TerrainArenaDrawPlanner.Plan listPlan = TerrainArenaDrawPlanner.plan(commands, 0);
        TerrainArenaDrawPlanner.Accumulator accumulator = TerrainArenaDrawPlanner.accumulator(0);
        for (TerrainArenaDrawPlanner.Command command : commands) accumulator.accept(command);
        TerrainArenaDrawPlanner.Plan streamingPlan = accumulator.finish();

        assertEquals(listPlan, streamingPlan);
    }

    @Test
    void combinesLayerPlansWithoutInventingCrossLayerBatches() {
        TerrainArenaDrawPlanner.Plan combined = TerrainArenaDrawPlanner.combine(
                new TerrainArenaDrawPlanner.Plan(8, 1, 3, 5L, 6, 2, 4L),
                new TerrainArenaDrawPlanner.Plan(4, 2, 2, 2L, 3, 1, 2L)
        );

        assertEquals(12, combined.eligibleCommands());
        assertEquals(3, combined.fallbackCommands());
        assertEquals(5, combined.arenaBatches());
        assertEquals(7L, combined.potentialBindReductions());
        assertEquals(9, combined.baseVertexReadyCommands());
        assertEquals(3, combined.baseVertexBatches());
        assertEquals(6L, combined.potentialBaseVertexBindReductions());
    }

    private static TerrainArenaDrawStateRegistry.DrawState state(int vertexBytes, int indexBytes) {
        return new TerrainArenaDrawStateRegistry.DrawState(
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                16,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                vertexBytes,
                indexBytes
        );
    }

    private static TerrainArenaDrawStateRegistry.DrawState sequentialState() {
        int bytes = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte() * 16;
        return state(bytes, 0);
    }

    private static TerrainArenaDrawPlanner.Command command(
            TerrainRegionAllocationRegistry.ArenaKey arena,
            long offset,
            TerrainArenaDrawStateRegistry.DrawState state,
            long generation
    ) {
        long required = state.requiredAllocationBytes();
        return new TerrainArenaDrawPlanner.Command(
                handle(arena, offset, required, required, generation),
                state
        );
    }

    private static TerrainRegionAllocationRegistry.Handle handle(
            TerrainRegionAllocationRegistry.ArenaKey arena,
            long offset,
            long size,
            long payload,
            long generation
    ) {
        return new TerrainRegionAllocationRegistry.Handle(arena, offset, size, payload, generation);
    }
}
