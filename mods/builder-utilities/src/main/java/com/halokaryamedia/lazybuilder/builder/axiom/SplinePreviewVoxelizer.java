package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts spline placement plans into a bounded set of preview voxels.
 */
public final class SplinePreviewVoxelizer {
    public static final int MAX_PREVIEW_VOXELS = 250_000;

    private SplinePreviewVoxelizer() {
    }

    public static List<Voxel> voxelize(List<SplinePlacementPlanEntry> plan) {
        Objects.requireNonNull(plan, "plan");
        Set<Voxel> voxels = new LinkedHashSet<>();
        for (SplinePlacementPlanEntry entry : plan) {
            Objects.requireNonNull(entry, "entry");
            add(voxels, entry.position());
            double radius = entry.radius();
            BuilderVec3 normal = entry.frame().normal().multiply(radius);
            BuilderVec3 binormal = entry.frame().binormal().multiply(radius);
            add(voxels, entry.position().add(normal));
            add(voxels, entry.position().subtract(normal));
            add(voxels, entry.position().add(binormal));
            add(voxels, entry.position().subtract(binormal));
            if (voxels.size() > MAX_PREVIEW_VOXELS) {
                throw new IllegalArgumentException("preview voxel count exceeds " + MAX_PREVIEW_VOXELS);
            }
        }
        return List.copyOf(voxels);
    }

    private static void add(Set<Voxel> voxels, BuilderVec3 position) {
        voxels.add(new Voxel(
                rounded(position.x()),
                rounded(position.y()),
                rounded(position.z())
        ));
    }

    private static int rounded(double value) {
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("preview coordinate exceeds integer world range");
        }
        return (int) rounded;
    }

    public record Voxel(int x, int y, int z) {
    }
}
