package com.halokaryamedia.lazybuilder.builder.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockBoundsTest {
    @Test
    void usesFloorChunkCoordinatesForNegativeBlocks() {
        BlockBounds bounds = new BlockBounds(-17, 0, -16, -1, 9, -1);
        assertEquals(-2, bounds.minChunkX());
        assertEquals(-1, bounds.maxChunkX());
        assertEquals(-1, bounds.minChunkZ());
        assertEquals(-1, bounds.maxChunkZ());
    }

    @Test
    void clipsIntersectionToRequestedChunk() {
        BlockBounds bounds = new BlockBounds(-2, 5, -2, 18, 6, 18);
        BlockBounds chunk = bounds.intersectionWithChunk(0, 0).orElseThrow();
        assertEquals(new BlockBounds(0, 5, 0, 15, 6, 15), chunk);
        assertEquals(512, chunk.blockCount());
    }

    @Test
    void rejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockBounds(2, 0, 0, 1, 0, 0));
    }
}
