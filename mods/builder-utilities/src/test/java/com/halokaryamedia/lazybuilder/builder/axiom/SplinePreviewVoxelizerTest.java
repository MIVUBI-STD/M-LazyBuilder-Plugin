package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.symmetry.SymmetryPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplinePreviewVoxelizerTest {
    private static final SplineFrame FRAME = new SplineFrame(
            new BuilderVec3(1, 0, 0), new BuilderVec3(0, 1, 0), new BuilderVec3(0, 0, 1));

    @Test
    void includesCenterAndOrientationRadiusRibs() {
        SplinePlacementPlanEntry entry = entry(0, new BuilderVec3(10, 64, 20), 2.0);
        List<SplinePreviewVoxelizer.Voxel> voxels = SplinePreviewVoxelizer.voxelize(List.of(entry));
        assertEquals(5, voxels.size());
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 66, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 62, 20)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 22)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(10, 64, 18)));
    }

    @Test
    void rotationalSymmetryProducesAdditionalPreviewCopies() {
        SplinePlacementPlanEntry entry = entry(0, new BuilderVec3(2, 64, 0), 0.1);
        var transforms = SymmetryPlanner.rotational(new BuilderVec3(0, 64, 0), new BuilderVec3(0, 1, 0), 4);
        List<SplinePreviewVoxelizer.Voxel> voxels = SplinePreviewVoxelizer.voxelize(List.of(entry), transforms);
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(2, 64, 0)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(0, 64, -2)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(-2, 64, 0)));
        assertTrue(voxels.contains(new SplinePreviewVoxelizer.Voxel(0, 64, 2)));
    }

    @Test
    void deduplicatesRoundedPreviewPoints() {
        PlacementTransform transform = new PlacementTransform(0, 1, false);
        List<SplinePlacementPlanEntry> plan = List.of(
                new SplinePlacementPlanEntry(0, new BuilderVec3(0.1, 64, 0.1), FRAME, 0.1, "a", transform),
                new SplinePlacementPlanEntry(1, new BuilderVec3(0.2, 64, 0.2), FRAME, 0.1, "b", transform));
        assertEquals(1, SplinePreviewVoxelizer.voxelize(plan).size());
    }

    private static SplinePlacementPlanEntry entry(int ordinal, BuilderVec3 position, double radius) {
        return new SplinePlacementPlanEntry(
                ordinal, position, FRAME, radius, "bridge", new PlacementTransform(0, 1, false));
    }
}
