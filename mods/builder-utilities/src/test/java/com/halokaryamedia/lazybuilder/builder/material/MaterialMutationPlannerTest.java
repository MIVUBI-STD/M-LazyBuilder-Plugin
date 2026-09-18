package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialMutationPlannerTest {
    @Test
    void skipsMaskedAndUnchangedBlocksWhileProducingHistoryReadyDelta() {
        ChunkWorkUnit unit = new ChunkWorkUnit(-1, -1, new BlockBounds(-2, 5, -2, -1, 5, -1));
        ChunkChangeSet changes = MaterialMutationPlanner.plan(
                unit,
                new BlockMaterial("minecraft:stone"),
                context -> context.x() == -2,
                new OperationSeed(7),
                (x, y, z) -> z == -1 ? "minecraft:stone" : "minecraft:dirt"
        );
        assertEquals(1, changes.size());
        assertEquals("minecraft:dirt", changes.beforeState(0));
        assertEquals("minecraft:stone", changes.afterState(0));
        long packed = changes.positions()[0];
        assertEquals(14, LocalBlockPosition.localX(packed));
        assertEquals(14, LocalBlockPosition.localZ(packed));
        assertEquals(5, LocalBlockPosition.y(packed));
    }

    @Test
    void sameSeedProducesSameChunkDeltaForRepeatedPlanning() {
        ChunkWorkUnit unit = new ChunkWorkUnit(0, 0, new BlockBounds(0, 0, 0, 15, 0, 15));
        BuilderMaterial material = new WeightedPaletteMaterial(java.util.List.of(
                new WeightedPaletteMaterial.Entry(new BlockMaterial("stone"), 3),
                new WeightedPaletteMaterial.Entry(new BlockMaterial("andesite"), 1)
        ), 4L);
        var first = MaterialMutationPlanner.plan(unit, material, MaterialMask.all(), new OperationSeed(55), (x,y,z) -> "air");
        var second = MaterialMutationPlanner.plan(unit, material, MaterialMask.all(), new OperationSeed(55), (x,y,z) -> "air");
        assertArrayEquals(first.positions(), second.positions());
        assertArrayEquals(first.beforeStates(), second.beforeStates());
        assertArrayEquals(first.afterStates(), second.afterStates());
        assertEquals(first.palette(), second.palette());
    }
}
