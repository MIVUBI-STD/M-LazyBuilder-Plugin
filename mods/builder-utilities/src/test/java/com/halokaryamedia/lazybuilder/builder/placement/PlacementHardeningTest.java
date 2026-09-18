package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlacementHardeningTest {
    @Test
    void slopeConstraintRejectsUnevenFootprint() {
        PlacementFootprint footprint = new PlacementFootprint(1, 1);
        PlacementConstraint flatEnough = PlacementConstraints.slope(
                (x, z) -> x == 1 ? 5 : 0,
                footprint,
                2
        );
        assertFalse(flatEnough.test(new PlacementPoint(0, 0, 0, 0)));
    }

    @Test
    void footprintCollisionFilterIsStableFirstWins() {
        List<PlacementPoint> result = FootprintCollisionFilter.filter(List.of(
                new PlacementPoint(0, 64, 0, 0),
                new PlacementPoint(1, 64, 1, 1),
                new PlacementPoint(8, 64, 8, 2)
        ), new PlacementFootprint(1, 1));

        assertEquals(List.of(
                new PlacementPoint(0, 64, 0, 0),
                new PlacementPoint(8, 64, 8, 2)
        ), result);
    }

    @Test
    void plannerCanComposeSlopeAndFootprintCollisionRules() {
        PlacementDistribution distribution = (bounds, surface, seed) -> List.of(
                new PlacementPoint(0, 64, 0, 0),
                new PlacementPoint(1, 64, 0, 1),
                new PlacementPoint(10, 64, 0, 2)
        );

        List<PlacementPlanEntry> plan = PlacementPlanner.plan(
                new BlockBounds(-16, 64, -16, 16, 64, 16),
                (x, z) -> 64,
                new OperationSeed(10L),
                distribution,
                (point, seed) -> "lazybuilder:test",
                new PlacementVariation(0, 0, 1, 1, 0, 0, 1L),
                PlacementConstraints.all(),
                new PlacementFootprint(1, 1),
                true
        );

        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0).point().ordinal());
        assertEquals(2, plan.get(1).point().ordinal());
    }

    @Test
    void placementGroupsPreserveChunkLocalOrder() {
        List<PlacementPlanEntry> plan = List.of(
                entry(-1, 64, -1, 0),
                entry(0, 64, 0, 1),
                entry(31, 64, 0, 2)
        );
        Map<PlacementChunkGroups.ChunkKey, List<PlacementPlanEntry>> grouped =
                PlacementChunkGroups.group(plan);

        assertEquals(3, grouped.size());
        assertEquals(1, grouped.get(new PlacementChunkGroups.ChunkKey(-1, -1)).size());
        assertEquals(1, grouped.get(new PlacementChunkGroups.ChunkKey(0, 0)).size());
        assertEquals(1, grouped.get(new PlacementChunkGroups.ChunkKey(1, 0)).size());
    }

    private static PlacementPlanEntry entry(int x, int y, int z, int ordinal) {
        return new PlacementPlanEntry(
                new PlacementPoint(x, y, z, ordinal),
                "lazybuilder:test",
                new PlacementTransform(0, 1, false)
        );
    }
}
