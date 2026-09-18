package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PointMirrorSymmetryTest {
    @Test
    void mirrorsAroundPivotAndDeduplicates() {
        var result = PointSymmetryPlanner.rotationalAndMirrors(
                List.of(new PlacementPoint(2, 64, 3, 0)),
                new BuilderVec3(0, 64, 0),
                1,
                true,
                true,
                16
        );

        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(p -> p.x() == 2 && p.z() == 3));
        assertTrue(result.stream().anyMatch(p -> p.x() == -2 && p.z() == 3));
        assertTrue(result.stream().anyMatch(p -> p.x() == 2 && p.z() == -3));
        assertTrue(result.stream().anyMatch(p -> p.x() == -2 && p.z() == -3));
    }

    @Test
    void pivotPointStillAppearsOnceAcrossAllSymmetries() {
        var result = PointSymmetryPlanner.rotationalAndMirrors(
                List.of(new PlacementPoint(0, 64, 0, 0)),
                new BuilderVec3(0, 64, 0),
                8,
                true,
                true,
                16
        );
        assertEquals(1, result.size());
    }
}
