package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementExpanderTest {
    private static final StructureSnapshot SINGLE = new StructureSnapshot(List.of(
            new StructureBlock(0, 0, 0, "minecraft:stone"),
            new StructureBlock(1, 0, 0, "minecraft:dirt")
    ));

    @Test
    void expandsMultipleSourcesIntoChunkDeltas() {
        List<PlacementPlanEntry> placements = List.of(
                placement(0, 64, 0, 0, "tree"),
                placement(32, 64, 0, 1, "tree")
        );

        List<ChunkChangeSet> chunks = StructurePlacementExpander.expand(
                placements,
                id -> SINGLE,
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air"
        );

        assertEquals(2, chunks.size());
        assertEquals(4, chunks.stream().mapToInt(ChunkChangeSet::size).sum());
    }

    @Test
    void rejectsWorldSpaceOverlapBeforeHistoryPlanning() {
        List<PlacementPlanEntry> placements = List.of(
                placement(0, 64, 0, 0, "a"),
                placement(1, 64, 0, 1, "b")
        );

        assertThrows(IllegalArgumentException.class, () ->
                StructurePlacementExpander.expand(
                        placements,
                        id -> SINGLE,
                        BlockStateTransform.identity(),
                        (x, y, z) -> "minecraft:air"
                ));
    }

    @Test
    void rejectsUnsupportedArbitraryYawAndScale() {
        PlacementPlanEntry yaw = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0),
                "tree",
                new PlacementTransform(45.0, 1.0, false)
        );
        PlacementPlanEntry scale = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0),
                "tree",
                new PlacementTransform(0.0, 2.0, false)
        );

        assertThrows(IllegalArgumentException.class, () ->
                StructurePlacementExpander.expand(
                        List.of(yaw), id -> SINGLE, BlockStateTransform.identity(),
                        (x, y, z) -> "minecraft:air"));

        assertThrows(IllegalArgumentException.class, () ->
                StructurePlacementExpander.expand(
                        List.of(scale), id -> SINGLE, BlockStateTransform.identity(),
                        (x, y, z) -> "minecraft:air"));
    }

    @Test
    void quarterTurnAndMirrorAreConcreteBlockSafe() {
        PlacementPlanEntry entry = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0),
                "tree",
                new PlacementTransform(90.0, 1.0, true)
        );

        List<ChunkChangeSet> chunks = StructurePlacementExpander.expand(
                List.of(entry),
                id -> SINGLE,
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air"
        );

        assertEquals(2, chunks.stream().mapToInt(ChunkChangeSet::size).sum());
    }

    private static PlacementPlanEntry placement(int x, int y, int z, int ordinal, String source) {
        return new PlacementPlanEntry(
                new PlacementPoint(x, y, z, ordinal),
                source,
                new PlacementTransform(0, 1, false)
        );
    }
}
