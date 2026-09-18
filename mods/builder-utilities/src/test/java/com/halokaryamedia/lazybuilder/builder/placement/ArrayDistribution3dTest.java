package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArrayDistribution3dTest {
    @Test
    void generatesVerticalAndDiagonalArraysDeterministically() {
        ArrayDistribution3d distribution =
                new ArrayDistribution3d(10, 64, -5, 4, 2, 3, -1);
        var points = distribution.generate(
                new BlockBounds(0, 0, -20, 30, 100, 10),
                (x, z) -> 0,
                new OperationSeed(1)
        );

        assertEquals(4, points.size());
        assertEquals(new PlacementPoint(10, 64, -5, 0), points.get(0));
        assertEquals(new PlacementPoint(16, 73, -8, 3), points.get(3));
    }

    @Test
    void rejectsZeroStepForMultipleEntries() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArrayDistribution3d(0, 64, 0, 2, 0, 0, 0));
    }
}
