package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementVariationMirrorTest {
    @Test
    void canDeterministicallyForceBothMirrorAxes() {
        PlacementVariation variation =
                new PlacementVariation(0, 0, 1, 1, 1, 1, 99L);
        PlacementTransform transform = variation.resolve(
                new PlacementPoint(1, 64, 2, 0),
                new OperationSeed(123L)
        );
        assertTrue(transform.mirrorX());
        assertTrue(transform.mirrorZ());
    }

    @Test
    void legacyConstructorLeavesMirrorZDisabled() {
        PlacementVariation variation =
                new PlacementVariation(0, 0, 1, 1, 1, 99L);
        PlacementTransform transform = variation.resolve(
                new PlacementPoint(1, 64, 2, 0),
                new OperationSeed(123L)
        );
        assertTrue(transform.mirrorX());
        assertFalse(transform.mirrorZ());
    }
}
