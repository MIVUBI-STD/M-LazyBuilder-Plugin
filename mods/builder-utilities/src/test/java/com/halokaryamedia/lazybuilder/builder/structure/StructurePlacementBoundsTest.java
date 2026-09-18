package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementBoundsTest {
    @Test
    void transformedBoundsRespectAnchorRotationAndNonCenteredLocalBounds() {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(
                        new StructureBlock(2, 0, -3, "minecraft:stone"),
                        new StructureBlock(5, 2, -1, "minecraft:dirt")
                )
        );
        StructurePlacement placement =
                new StructurePlacement(100, 64, 200, 1, false, false);

        StructurePlacementBounds bounds =
                StructurePlacementBounds.of(snapshot, placement);

        assertEquals(101, bounds.minX());
        assertEquals(103, bounds.maxX());
        assertEquals(64, bounds.minY());
        assertEquals(66, bounds.maxY());
        assertEquals(202, bounds.minZ());
        assertEquals(205, bounds.maxZ());
    }

    @Test
    void overlapUsesAllThreeAxes() {
        StructurePlacementBounds a =
                new StructurePlacementBounds(0, 0, 0, 10, 10, 10);
        assertTrue(a.overlaps(
                new StructurePlacementBounds(10, 10, 10, 20, 20, 20)));
        assertFalse(a.overlaps(
                new StructurePlacementBounds(0, 11, 0, 10, 20, 10)));
    }
}
