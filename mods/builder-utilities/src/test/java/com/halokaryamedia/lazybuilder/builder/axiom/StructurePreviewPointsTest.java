package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructurePreviewPointsTest {
    @Test
    void previewUsesSameIntegerPlacementTransformAsPastePlanner() {
        StructureSnapshot snapshot = new StructureSnapshot(List.of(
                new StructureBlock(1, 0, 0, "minecraft:stone")
        ));
        var points = StructurePreviewPoints.create(
                snapshot,
                new StructurePlacement(10, 64, 10, 1, false, false)
        );
        assertEquals(10, points.get(0).x());
        assertEquals(64, points.get(0).y());
        assertEquals(11, points.get(0).z());
    }
}
