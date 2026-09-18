package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementAuxiliaryBatchTest {
    @Test
    void batchCarriesAllAuxiliaryPayloadTypesWithUniqueEntityKeys() throws Exception {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{2})),
                List.of(new StructureBiomeSample(0, 0, 0, new byte[]{4})),
                List.of(new StructureEntity(0.5, 0.5, 0.5, new byte[]{6}))
        );

        StructureAuxiliaryContext auxiliary = new StructureAuxiliaryContext(
                (x, y, z) -> new byte[]{1},
                BlockEntityPayloadTransform.identity(),
                (x, y, z) -> new byte[]{3},
                BiomePayloadTransform.identity(),
                (key, pos) -> EntityExtensionPayload.absent(
                        pos.x(), pos.y(), pos.z()).encode(),
                EntityPayloadTransform.identity()
        );

        StructurePastePlan plan = StructurePlacementBatchPlanner.planAll(
                List.of(entry(0, 64, 0, 0), entry(32, 64, 0, 1)),
                id -> snapshot,
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air",
                auxiliary
        );

        assertEquals(6, plan.extensionChanges());
        assertEquals(2, plan.extensions().stream()
                .filter(f -> f.typeId().equals(HistoryExtensionTypes.BLOCK_ENTITY)).count());
        assertEquals(2, plan.extensions().stream()
                .filter(f -> f.typeId().equals(HistoryExtensionTypes.BIOME)).count());
        var entities = plan.extensions().stream()
                .filter(f -> f.typeId().equals(HistoryExtensionTypes.ENTITY))
                .toList();
        assertEquals(2, entities.size());
        assertNotEquals(entities.get(0).localKey(), entities.get(1).localKey());

        EntityExtensionPayload firstBefore =
                EntityExtensionPayload.decode(entities.get(0).beforePayload());
        EntityExtensionPayload firstAfter =
                EntityExtensionPayload.decode(entities.get(0).afterPayload());
        assertFalse(firstBefore.present());
        assertTrue(firstAfter.present());
        assertTrue(firstBefore.sameSlot(firstAfter));
        assertArrayEquals(new byte[]{6}, firstAfter.templateNbt());
    }

    private static PlacementPlanEntry entry(int x, int y, int z, int ordinal) {
        return new PlacementPlanEntry(
                new PlacementPoint(x, y, z, ordinal),
                "schematic",
                new PlacementTransform(0, 1, false)
        );
    }
}
