package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkChangeSetBuilderTest {
    @Test
    void coalescesContinuousDuplicateWritesAndRemovesNetNoOp() {
        ChunkChangeSetBuilder builder = new ChunkChangeSetBuilder(0, 0);
        builder.addWorld(1, 64, 2, "minecraft:stone", "minecraft:dirt");
        builder.addWorld(1, 64, 2, "minecraft:dirt", "minecraft:gold_block");

        ChunkChangeSet change = builder.build();
        assertEquals(1, change.size());
        assertEquals("minecraft:stone", change.beforeState(0));
        assertEquals("minecraft:gold_block", change.afterState(0));

        builder.addWorld(1, 64, 2, "minecraft:gold_block", "minecraft:stone");
        assertEquals(0, builder.build().size());
    }

    @Test
    void rejectsNonContiguousDuplicateWriteChain() {
        ChunkChangeSetBuilder builder = new ChunkChangeSetBuilder(0, 0);
        builder.addWorld(1, 64, 2, "minecraft:stone", "minecraft:dirt");
        assertThrows(IllegalArgumentException.class, () ->
                builder.addWorld(1, 64, 2, "minecraft:air", "minecraft:gold_block"));
    }

    @Test
    void rejectsCoordinatesOutsideChunk() {
        ChunkChangeSetBuilder builder = new ChunkChangeSetBuilder(0, 0);
        assertThrows(IllegalArgumentException.class, () ->
                builder.addWorld(16, 64, 0, "minecraft:stone", "minecraft:air"));
    }
}
