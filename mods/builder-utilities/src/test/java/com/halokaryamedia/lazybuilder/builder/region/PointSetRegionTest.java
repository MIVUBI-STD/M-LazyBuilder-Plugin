package com.halokaryamedia.lazybuilder.builder.region;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PointSetRegionTest {
    @Test void plansOnlyOccupiedChunksAndPreservesExactMembership() {
        PointSetRegion region = new PointSetRegion(List.of(
                new PointSetRegion.Point(-1, 64, -1),
                new PointSetRegion.Point(0, 70, 0),
                new PointSetRegion.Point(31, 80, 0)));
        List<ChunkWorkUnit> units = new DeterministicRegionPlanner().plan(region);
        assertEquals(3, units.size());
        assertTrue(region.contains(-1, 64, -1));
        assertFalse(region.contains(1, 70, 0));
        assertEquals(List.of(-1, 0, 1), units.stream().map(ChunkWorkUnit::chunkX).toList());
    }
}
