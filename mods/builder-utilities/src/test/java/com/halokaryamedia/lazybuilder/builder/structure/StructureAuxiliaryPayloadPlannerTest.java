package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureAuxiliaryPayloadPlannerTest {
    @Test
    void preservesBiomeAndEntityPayloadsAsTypedExtensions() throws Exception {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:stone")),
                List.of(),
                List.of(new StructureBiomeSample(0, 0, 0, new byte[]{2})),
                List.of(new StructureEntity(0.5, 0.5, 0.5, new byte[]{4}))
        );

        var frames = StructureAuxiliaryPayloadPlanner.plan(
                snapshot,
                new StructurePlacement(32, 64, -1, 1, false, false),
                (x, y, z) -> new byte[]{1},
                BiomePayloadTransform.identity(),
                (key, pos) -> EntityExtensionPayload
                        .absent(pos.x(), pos.y(), pos.z())
                        .encode(),
                EntityPayloadTransform.identity()
        );

        assertEquals(2, frames.size());
        assertEquals(HistoryExtensionTypes.BIOME, frames.get(0).typeId());
        assertEquals(HistoryExtensionTypes.ENTITY, frames.get(1).typeId());
        assertArrayEquals(new byte[]{2}, frames.get(0).afterPayload());

        EntityExtensionPayload beforeEntity =
                EntityExtensionPayload.decode(frames.get(1).beforePayload());
        EntityExtensionPayload afterEntity =
                EntityExtensionPayload.decode(frames.get(1).afterPayload());
        assertFalse(beforeEntity.present());
        assertTrue(afterEntity.present());
        assertTrue(beforeEntity.sameSlot(afterEntity));
        assertArrayEquals(new byte[]{4}, afterEntity.templateNbt());
    }

    @Test
    void floatingEntityPositionRotatesWithPlacement() {
        StructurePlacement.WorldPositionD pos =
                new StructurePlacement(10, 20, 30, 1, false, false)
                        .transform(1.5, 2.0, 0.5);
        assertEquals(9.5, pos.x(), 1.0e-9);
        assertEquals(22.0, pos.y(), 1.0e-9);
        assertEquals(31.5, pos.z(), 1.0e-9);
    }
}
