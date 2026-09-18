package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementCompilerTest {
    private static final StructureTemplate TEMPLATE = StructureTemplate.of(
            "test:pair",
            new StructureBlock(0, 0, 0, "minecraft:stone"),
            new StructureBlock(1, 0, 0, "minecraft:dirt")
    );

    @Test
    void rotatesMirrorsAndChunksStructureBlocksDeterministically() {
        PlacementPlanEntry placement = new PlacementPlanEntry(
                new PlacementPoint(-1, 64, -1, 0),
                "test:pair",
                new PlacementTransform(90, 1, true)
        );
        List<ChunkChangeSet> chunks = StructurePlacementCompiler.compile(
                List.of(placement),
                id -> TEMPLATE,
                (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.ERROR
        );

        assertEquals(2, chunks.stream().mapToInt(ChunkChangeSet::size).sum());
        assertEquals(2, chunks.size());
    }

    @Test
    void overlapPolicyIsExplicit() {
        PlacementPlanEntry a = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0), "test:pair",
                new PlacementTransform(0, 1, false));
        PlacementPlanEntry b = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 1), "test:pair",
                new PlacementTransform(0, 1, false));

        assertThrows(IllegalArgumentException.class, () -> StructurePlacementCompiler.compile(
                List.of(a, b), id -> TEMPLATE, (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.ERROR));

        List<ChunkChangeSet> firstWins = StructurePlacementCompiler.compile(
                List.of(a, b), id -> TEMPLATE, (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.FIRST_WINS);
        assertEquals(2, firstWins.stream().mapToInt(ChunkChangeSet::size).sum());
    }

    @Test
    void arbitraryYawAndScaleAreRejectedRatherThanLossilyResampled() {
        PlacementPlanEntry yaw = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0), "test:pair",
                new PlacementTransform(45, 1, false));
        PlacementPlanEntry scale = new PlacementPlanEntry(
                new PlacementPoint(0, 64, 0, 0), "test:pair",
                new PlacementTransform(0, 2, false));

        assertThrows(IllegalArgumentException.class, () -> StructurePlacementCompiler.compile(
                List.of(yaw), id -> TEMPLATE, (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.ERROR));
        assertThrows(IllegalArgumentException.class, () -> StructurePlacementCompiler.compile(
                List.of(scale), id -> TEMPLATE, (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.ERROR));
    }
}
