package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BlockSideVisibilityCacheTest {
    @Test
    void cachesIdentityTupleWithoutChangingResult() {
        BlockSideVisibilityCache cache = new BlockSideVisibilityCache();
        var stone = Blocks.STONE.getDefaultState();
        var glass = Blocks.GLASS.getDefaultState();

        assertNull(cache.get(stone, glass, Direction.NORTH));
        cache.put(stone, glass, Direction.NORTH, true);

        assertEquals(Boolean.TRUE, cache.get(stone, glass, Direction.NORTH));
        assertEquals(1, cache.size());
    }

    @Test
    void repeatedPutDoesNotGrowCache() {
        BlockSideVisibilityCache cache = new BlockSideVisibilityCache();
        var stone = Blocks.STONE.getDefaultState();
        var dirt = Blocks.DIRT.getDefaultState();

        cache.put(stone, dirt, Direction.UP, false);
        cache.put(stone, dirt, Direction.UP, false);

        assertEquals(Boolean.FALSE, cache.get(stone, dirt, Direction.UP));
        assertEquals(1, cache.size());
    }
}
