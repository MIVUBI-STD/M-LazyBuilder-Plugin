package com.halokaryamedia.lazybuilder.builder.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementSlopeConstraintTest {
    @Test
    void flatFootprintPassesAndSteepFootprintFails() {
        PlacementPoint point = new PlacementPoint(0, 64, 0, 0);
        PlacementFootprint footprint = new PlacementFootprint(1, 1);

        PlacementConstraint flat = PlacementConstraints.slope(
                (x, z) -> 64,
                footprint,
                0
        );
        assertTrue(flat.test(point));

        PlacementConstraint steep = PlacementConstraints.slope(
                (x, z) -> x > 0 ? 70 : 64,
                footprint,
                2
        );
        assertFalse(steep.test(point));
    }
}
