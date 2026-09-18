package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructurePlacementBatchSharedBuilderTest {
    @Test
    void producesOneDeterministicFramePerChunk() {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(
                        new StructureBlock(0, 0, 0, "minecraft:stone"),
                        new StructureBlock(1, 0, 0, "minecraft:dirt")
                ),
                List.of(), List.of(), List.of()
        );
        List<PlacementPlanEntry> placements = List.of(
                new PlacementPlanEntry(
                        new PlacementPoint(0, 64, 0, 0),
                        "test:pair",
                        new PlacementTransform(0, 1, false)),
                new PlacementPlanEntry(
                        new PlacementPoint(16, 64, 0, 1),
                        "test:pair",
                        new PlacementTransform(0, 1, false))
        );

        StructurePastePlan plan = StructurePlacementBatchPlanner.planBlocks(
                placements,
                id -> snapshot,
                (state, placement) -> state,
                (x, y, z) -> "minecraft:air"
        );

        assertEquals(2, plan.chunks().size());
        assertEquals(4, plan.blockChanges());
    }
}
