package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementBatchPlannerTest {
    @Test
    void mergesManyPlacementsIntoOneFramePerChunk() {
        StructureSnapshot single = new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:stone")
        ));
        List<PlacementPlanEntry> placements = List.of(
                entry(0, 64, 0, 0),
                entry(1, 64, 0, 1),
                entry(32, 64, 0, 2)
        );

        StructurePastePlan plan = StructurePlacementBatchPlanner.planBlocks(
                placements,
                id -> single,
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air"
        );

        assertEquals(2, plan.chunks().size());
        assertEquals(3, plan.blockChanges());
    }

    @Test
    void carriesBlockEntityPayloadsAcrossPlacementBatch() throws Exception {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{2}))
        );
        StructurePastePlan plan = StructurePlacementBatchPlanner.plan(
                List.of(entry(0, 64, 0, 0)),
                id -> snapshot,
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air",
                (x, y, z) -> new byte[]{1},
                BlockEntityPayloadTransform.identity()
        );

        assertEquals(1, plan.extensionChanges());
        assertEquals(HistoryExtensionTypes.BLOCK_ENTITY, plan.extensions().get(0).typeId());
    }

    @Test
    void overlappingStructuresAreRejectedBeforeDurablePrepare() {
        StructureSnapshot twoWide = new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:stone"),
                new StructureBlock(1, 0, 0, "minecraft:stone")
        ));

        assertThrows(IllegalArgumentException.class, () ->
                StructurePlacementBatchPlanner.planBlocks(
                        List.of(entry(0, 64, 0, 0), entry(1, 64, 0, 1)),
                        id -> twoWide,
                        BlockStateTransform.identity(),
                        (x, y, z) -> "minecraft:air"
                ));
    }

    private static PlacementPlanEntry entry(int x, int y, int z, int ordinal) {
        return new PlacementPlanEntry(
                new PlacementPoint(x, y, z, ordinal),
                "lazybuilder:test",
                new PlacementTransform(0, 1, false)
        );
    }
}
