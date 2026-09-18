package com.halokaryamedia.lazybuilder.builder.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementBiomeConstraintTest {
    @Test
    void filtersByStableBiomeIdentity() {
        PlacementConstraint plains = PlacementConstraints.biomeEquals(
                (x, y, z) -> x < 0 ? "minecraft:forest" : "minecraft:plains",
                "minecraft:plains"
        );
        assertTrue(plains.test(new PlacementPoint(1, 64, 0, 0)));
        assertFalse(plains.test(new PlacementPoint(-1, 64, 0, 1)));
    }
}
