package com.halokaryamedia.lazybuilder.builder.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicRegionPlannerTest {
    private final RegionPlanner planner = new DeterministicRegionPlanner();

    @Test
    void emitsOneWorkUnitForSingleChunkRegion() {
        BoxRegion region = new BoxRegion(new BlockBounds(1, 2, 3, 5, 4, 7));
        List<ChunkWorkUnit> work = planner.plan(region);

        assertEquals(1, work.size());
        assertEquals(0, work.getFirst().chunkX());
        assertEquals(0, work.getFirst().chunkZ());
        assertEquals(region.bounds(), work.getFirst().candidateBounds());
    }

    @Test
    void plansCrossChunkRegionInStableZThenXOrderIncludingNegativeChunks() {
        BoxRegion region = new BoxRegion(new BlockBounds(-17, 0, -1, 16, 0, 16));
        List<ChunkWorkUnit> work = planner.plan(region);

        assertEquals(12, work.size());
        int[][] expected = {
                {-2, -1}, {-1, -1}, {0, -1}, {1, -1},
                {-2, 0}, {-1, 0}, {0, 0}, {1, 0},
                {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], work.get(i).chunkX());
            assertEquals(expected[i][1], work.get(i).chunkZ());
        }
        assertEquals(region.bounds().blockCount(),
                work.stream().mapToLong(ChunkWorkUnit::candidateBlockCount).sum());
    }

    @Test
    void repeatedPlanningIsDeterministic() {
        BoxRegion region = new BoxRegion(new BlockBounds(-32, -4, -32, 32, 4, 32));
        assertEquals(planner.plan(region), planner.plan(region));
    }
}
