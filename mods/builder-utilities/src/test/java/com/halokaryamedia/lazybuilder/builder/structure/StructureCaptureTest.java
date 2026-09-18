package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureTest {
    @Test
    void captureRebasesWorldCoordinatesAndCanSkipAir() {
        StructureSnapshot snapshot = StructureCapture.capture(
                new BlockBounds(10, 64, -3, 11, 64, -3),
                (x, y, z) -> x == 10 ? "minecraft:stone" : "minecraft:air",
                false
        );

        assertEquals(1, snapshot.blockCount());
        StructureBlock block = snapshot.blocks().get(0);
        assertEquals(0, block.x());
        assertEquals(0, block.y());
        assertEquals(0, block.z());
        assertEquals("minecraft:stone", block.blockState());
    }

    @Test
    void captureLimitIsEnforcedBeforeScanning() {
        assertThrows(IllegalArgumentException.class, () ->
                StructureCapture.capture(
                        new BlockBounds(0, 0, 0, 1000, 1000, 1),
                        (x, y, z) -> "minecraft:stone",
                        true
                ));
    }
}
