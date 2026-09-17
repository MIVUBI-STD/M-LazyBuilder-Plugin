package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplinePreviewVoxelizerTest {
    @Test
    void includesCenterAndOrientationRadiusRibs() {
        SplineFrame frame = new SplineFrame(
                new BuilderVec3(1, 0, 0),
                new BuilderVec3(0, 1, 0),
                new BuilderVec3(0, 0, 1)
        );
        SplinePlacementPlanEntry entry = new SplinePlacementPlanEntry(
                0,
                new BuilderVec3(10, 64, 20),
                frame,
                2.0,
                "bridge",
                new PlacementTransform(0, 1, false)
        );

        List<SplinePreviewVoxelizer.Voxel> voxels = SplinePreviewVoxelizer.voxelize(List.of(entry));

        assertEquals(5, voxels.size());
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 66, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 62, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 22)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 18)));
    }

    @Test
    void deduplicatesRoundedPreviewPoints() {
        SplineFrame frame = new SplineFrame(
                new BuilderVec3(1, 0, 0),
                new BuilderVec3(0, 1, 0),
                new BuilderVec3(0, 0, 1)
        );
        PlacementTransform transform = new PlacementTransform(0, 1, false);
        List<SplinePlacementPlanEntry> plan = List.of(
                new SplinePlacementPlanEntry(0, new BuilderVec3(0.1, 64, 0.1), frame, 0.1, "a", transform),
                new SplinePlacementPlanEntry(1, new BuilderVec3(0.2, 64, 0.2), frame, 0.1, "b", transform)
        );

        assertEquals(1, SplinePreviewVoxelizer.voxelize(plan).size());
    }
}
