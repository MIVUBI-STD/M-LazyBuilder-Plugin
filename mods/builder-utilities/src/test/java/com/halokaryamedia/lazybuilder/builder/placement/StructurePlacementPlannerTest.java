package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructurePlacementPlannerTest {
    @Test
    void usesPerSourceScaledRotatedFootprintsForCollisionRejection() {
        PlacementDistribution distribution = (bounds, surface, seed) -> List.of(
                new PlacementPoint(0, 64, 0, 0),
                new PlacementPoint(4, 64, 0, 1),
                new PlacementPoint(12, 64, 0, 2)
        );
        PlacementSource source = (point, seed) -> point.ordinal() == 1 ? "large" : "small";
        PlacementVariation variation = new PlacementVariation(45, 45, 1, 1, 0, 0, 0);

        List<PlacementPlanEntry> plan = StructurePlacementPlanner.plan(
                new BlockBounds(-32, 64, -32, 32, 64, 32),
                (x, z) -> 64,
                new OperationSeed(1),
                distribution,
                source,
                variation,
                PlacementConstraints.all(),
                id -> id.equals("large") ? new StructureFootprint(10, 10) : new StructureFootprint(3, 3),
                true
        );

        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0).point().ordinal());
        assertEquals(2, plan.get(1).point().ordinal());
    }
}
