package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.halokaryamedia.lazybuilder.builder.region.PointSetRegion;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialSparseRegionTest {
    @Test void mutationPlannerDoesNotFillSparseBoundingBox() {
        PointSetRegion region = new PointSetRegion(List.of(
                new PointSetRegion.Point(0, 64, 0), new PointSetRegion.Point(15, 64, 15)));
        var unit = new DeterministicRegionPlanner().plan(region).getFirst();
        ChunkChangeSet result = MaterialMutationPlanner.plan(unit, region,
                new BlockMaterial("minecraft:dirt"), MaterialMask.all(), new OperationSeed(7),
                (x, y, z) -> "minecraft:stone");
        assertEquals(2, result.size());
    }
}
