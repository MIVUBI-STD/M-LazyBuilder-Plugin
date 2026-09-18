package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePastePlannerTest {
    @Test
    void placementRotatesMirrorsAndGroupsAcrossChunks() {
        StructureSnapshot snapshot = new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:stone"),
                new StructureBlock(2, 0, 0, "minecraft:dirt"),
                new StructureBlock(17, 0, 0, "minecraft:glass")
        ));

        List<ChunkChangeSet> chunks = StructurePastePlanner.plan(
                snapshot,
                new StructurePlacement(-1, 64, -1, 1, true, false),
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air"
        );

        assertFalse(chunks.isEmpty());
        assertEquals(3, chunks.stream().mapToInt(ChunkChangeSet::size).sum());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.palette().contains("minecraft:air")));
    }

    @Test
    void skipsNoOpBlocksAndAppliesStateTransformHook() {
        StructureSnapshot snapshot = new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:oak_stairs[facing=north]"),
                new StructureBlock(1, 0, 0, "minecraft:stone")
        ));

        List<ChunkChangeSet> chunks = StructurePastePlanner.plan(
                snapshot,
                new StructurePlacement(0, 70, 0, 1, false, false),
                (state, placement) -> state.contains("oak_stairs")
                        ? "minecraft:oak_stairs[facing=east]"
                        : state,
                (x, y, z) -> x == 0
                        ? "minecraft:oak_stairs[facing=east]"
                        : "minecraft:air"
        );

        assertEquals(1, chunks.stream().mapToInt(ChunkChangeSet::size).sum());
        ChunkChangeSet only = chunks.get(0);
        assertEquals("minecraft:stone", only.afterState(0));
    }

    @Test
    void negativeWorldCoordinatesUseFloorModLocals() {
        StructureSnapshot snapshot = new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:stone")
        ));

        ChunkChangeSet chunk = StructurePastePlanner.plan(
                snapshot,
                new StructurePlacement(-1, 12, -1, 0, false, false),
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air"
        ).get(0);

        assertEquals(-1, chunk.chunkX());
        assertEquals(-1, chunk.chunkZ());
        assertEquals(15, LocalBlockPosition.localX(chunk.positions()[0]));
        assertEquals(15, LocalBlockPosition.localZ(chunk.positions()[0]));
    }

    @Test
    void duplicateLocalPositionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(List.of(
                new StructureBlock(0, 0, 0, "minecraft:stone"),
                new StructureBlock(0, 0, 0, "minecraft:dirt")
        )));
    }
}
