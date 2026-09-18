package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureBlockEntityPlanTest {
    @Test
    void blockEntityPayloadsBecomeHistoryExtensions() throws Exception {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{2, 3}))
        );

        StructurePastePlan plan = StructurePastePlanner.planWithBlockEntities(
                snapshot,
                new StructurePlacement(-1, 64, -1, 0, false, false),
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air",
                (x, y, z) -> new byte[]{1},
                BlockEntityPayloadTransform.identity()
        );

        assertEquals(1, plan.blockChanges());
        assertEquals(1, plan.extensionChanges());
        var extension = plan.extensions().get(0);
        assertEquals("lazybuilder:block_entity", extension.typeId());
        assertEquals(-1, extension.chunkX());
        assertEquals(-1, extension.chunkZ());
        assertArrayEquals(new byte[]{1}, extension.beforePayload());
        assertArrayEquals(new byte[]{2, 3}, extension.afterPayload());
    }

    @Test
    void blockEntityMustBelongToExistingStructureBlock() {
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:stone")),
                List.of(new StructureBlockEntity(1, 0, 0, new byte[]{1}))
        ));
    }
}
