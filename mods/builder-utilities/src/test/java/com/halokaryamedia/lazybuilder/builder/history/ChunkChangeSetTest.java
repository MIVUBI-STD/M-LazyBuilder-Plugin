package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkChangeSetTest {
    @Test
    void defensivelyCopiesPrimitiveArrays() {
        long[] positions = {LocalBlockPosition.pack(1, 64, 2)};
        int[] before = {0};
        int[] after = {1};
        ChunkChangeSet chunk = new ChunkChangeSet(0, 0, List.of("minecraft:stone", "minecraft:air"),
                positions, before, after);

        positions[0] = 0;
        before[0] = 1;
        after[0] = 0;

        assertEquals(1, LocalBlockPosition.localX(chunk.positions()[0]));
        assertEquals("minecraft:stone", chunk.beforeState(0));
        assertEquals("minecraft:air", chunk.afterState(0));
    }

    @Test
    void rejectsInvalidPaletteIndexes() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkChangeSet(
                0, 0, List.of("minecraft:stone"), new long[]{0}, new int[]{0}, new int[]{1}));
    }
    @Test
    void rejectsDuplicateLocalPositions() {
        long position = LocalBlockPosition.pack(1, 64, 2);
        assertThrows(IllegalArgumentException.class, () -> new ChunkChangeSet(
                0, 0,
                java.util.List.of("minecraft:stone", "minecraft:air"),
                new long[]{position, position},
                new int[]{0, 0},
                new int[]{1, 1}
        ));
    }

}
