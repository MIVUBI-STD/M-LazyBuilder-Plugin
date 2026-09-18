package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureSnapshotBoundsTest {
    private static final List<StructureBlock> BLOCKS = List.of(
            new StructureBlock(0, 0, 0, "minecraft:stone"),
            new StructureBlock(1, 1, 1, "minecraft:stone")
    );

    @Test
    void rejectsBiomeOutsideExportedBlockBounds() {
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(
                BLOCKS,
                List.of(),
                List.of(new StructureBiomeSample(
                        2, 0, 0, "minecraft:plains".getBytes(StandardCharsets.UTF_8))),
                List.of()
        ));
    }

    @Test
    void rejectsEntityOutsideExportedBlockVolume() {
        assertThrows(IllegalArgumentException.class, () -> new StructureSnapshot(
                BLOCKS,
                List.of(),
                List.of(),
                List.of(new StructureEntity(2.0, 0.5, 0.5, new byte[]{1}))
        ));
    }

    @Test
    void acceptsEntityInsideUpperExclusiveBlockVolume() {
        assertDoesNotThrow(() -> new StructureSnapshot(
                BLOCKS,
                List.of(),
                List.of(),
                List.of(new StructureEntity(1.999, 1.999, 1.999, new byte[]{1}))
        ));
    }
}
