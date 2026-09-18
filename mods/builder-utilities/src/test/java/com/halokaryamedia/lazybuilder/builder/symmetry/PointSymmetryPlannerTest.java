package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PointSymmetryPlannerTest {
    @Test
    void rotationalCopiesDeduplicatePivotAndReassignStableOrdinals() {
        List<PlacementPoint> result = PointSymmetryPlanner.rotational(
                List.of(
                        new PlacementPoint(0, 64, 0, 0),
                        new PlacementPoint(2, 64, 0, 1)
                ),
                new BuilderVec3(0, 64, 0),
                4,
                100
        );

        assertEquals(5, result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).ordinal());
        }
        assertTrue(result.contains(new PlacementPoint(0, 64, 0, 0)));
    }

    @Test
    void limitIsEnforcedAfterDeduplication() {
        assertThrows(IllegalArgumentException.class, () ->
                PointSymmetryPlanner.rotational(
                        List.of(new PlacementPoint(10, 64, 0, 0)),
                        new BuilderVec3(0, 64, 0),
                        8,
                        4
                ));
    }
}
