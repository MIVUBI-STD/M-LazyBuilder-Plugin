package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementAdapterMirrorTest {
    @Test
    void placementTransformCarriesBothMirrorAxes() {
        PlacementPlanEntry entry = new PlacementPlanEntry(
                new PlacementPoint(10, 64, 20, 0),
                "test:structure",
                new PlacementTransform(90, 1, true, true)
        );
        StructurePlacement placement = StructurePlacementAdapter.from(entry);

        assertTrue(placement.mirrorX());
        assertTrue(placement.mirrorZ());
        assertEquals(1, placement.quarterTurnsY());
    }

    @Test
    void legacyThreeArgumentTransformDefaultsMirrorZOff() {
        PlacementTransform legacy = new PlacementTransform(0, 1, true);
        assertTrue(legacy.mirrorX());
        assertFalse(legacy.mirrorZ());
    }
}
