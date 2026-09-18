package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicPlacementsTest {
    @Test
    void spongeOffsetRotatesWithSchematicOrientation() {
        SpongeSchematicImport imported = new SpongeSchematicImport(
                new StructureSnapshot(List.of(
                        new StructureBlock(0, 0, 0, "minecraft:stone")
                )),
                2, 1, 0, 4189
        );

        StructurePlacement placement = SchematicPlacements.atPasteBase(
                imported,
                new BlockPos(10, 64, 10),
                1,
                false,
                false
        );

        var origin = placement.transform(0, 0, 0);
        assertEquals(10, origin.x());
        assertEquals(65, origin.y());
        assertEquals(12, origin.z());
    }
}
