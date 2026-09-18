package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePreviewDecimationTest {
    @Test
    void decimatesLargeStructurePreviewWithoutChangingApplySource() {
        List<StructureBlock> blocks = new ArrayList<>();
        for (int i = 0; i < 300_000; i++) {
            blocks.add(new StructureBlock(i % 1000, i / 1000, 0, "minecraft:stone"));
        }
        StructureSnapshot snapshot = new StructureSnapshot(
                blocks, List.of(), List.of(), List.of());

        var preview = StructurePreviewPoints.create(
                snapshot,
                new StructurePlacement(0, 0, 0, 0, false, false)
        );
        assertTrue(StructurePreviewPoints.isDecimated(snapshot.blockCount()));
        assertTrue(preview.size() <= StructurePreviewPoints.MAX_PREVIEW_POINTS);
        assertFalse(preview.isEmpty());
        assertEquals(300_000, snapshot.blockCount());
    }
}
