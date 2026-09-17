package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainArenaDrawPlannerTest {
    @Test
    void batchesOnlyConsecutiveCommandsFromSameArena() {
        var arenaA = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var arenaB = new TerrainRegionAllocationRegistry.ArenaKey(1, 0, 0, 0);

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(
                handle(arenaA, 0L, 512L, 400L, 1L),
                handle(arenaA, 512L, 512L, 300L, 2L),
                handle(arenaB, 0L, 512L, 256L, 3L),
                handle(arenaB, 512L, 512L, 256L, 4L)
        ), 0);

        assertEquals(4, plan.eligibleCommands());
        assertEquals(0, plan.fallbackCommands());
        assertEquals(2, plan.arenaBatches());
        assertEquals(2L, plan.potentialBindReductions());
    }

    @Test
    void fallbackBreaksArenaBatchAndPreservesOrderBoundary() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 2);

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(Arrays.asList(
                handle(arena, 0L, 512L, 256L, 1L),
                handle(arena, 512L, 512L, 256L, 2L),
                null,
                handle(arena, 1024L, 512L, 256L, 3L)
        ), 2);

        assertEquals(3, plan.eligibleCommands());
        assertEquals(1, plan.fallbackCommands());
        assertEquals(2, plan.arenaBatches());
        assertEquals(1L, plan.potentialBindReductions());
    }

    @Test
    void rejectsWrongLayerEmptyAndUndersizedHandles() {
        var expected = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 1);
        var wrongLayer = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 3);

        TerrainArenaDrawPlanner.Plan plan = TerrainArenaDrawPlanner.plan(List.of(
                handle(expected, 0L, 512L, 0L, 1L),
                handle(expected, 512L, 128L, 300L, 2L),
                handle(wrongLayer, 1024L, 512L, 256L, 3L)
        ), 1);

        assertEquals(0, plan.eligibleCommands());
        assertEquals(3, plan.fallbackCommands());
        assertEquals(0, plan.arenaBatches());
        assertEquals(0L, plan.potentialBindReductions());
    }

    @Test
    void combinesLayerPlansWithoutInventingCrossLayerBatches() {
        TerrainArenaDrawPlanner.Plan combined = TerrainArenaDrawPlanner.combine(
                new TerrainArenaDrawPlanner.Plan(8, 1, 3, 5L),
                new TerrainArenaDrawPlanner.Plan(4, 2, 2, 2L)
        );

        assertEquals(12, combined.eligibleCommands());
        assertEquals(3, combined.fallbackCommands());
        assertEquals(5, combined.arenaBatches());
        assertEquals(7L, combined.potentialBindReductions());
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
