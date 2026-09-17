package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.symmetry.BuilderTransform;

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
        return voxelize(plan, List.of(BuilderTransform.identity()));
    }

    public static List<Voxel> voxelize(List<SplinePlacementPlanEntry> plan, List<BuilderTransform> transforms) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(transforms, "transforms");
        if (transforms.isEmpty()) {
            throw new IllegalArgumentException("preview transforms must not be empty");
        }
        Set<Voxel> voxels = new LinkedHashSet<>();
        for (BuilderTransform transform : transforms) {
            Objects.requireNonNull(transform, "transform");
            for (SplinePlacementPlanEntry entry : plan) {
                addEntry(voxels, Objects.requireNonNull(entry, "entry"), transform);
                if (voxels.size() > MAX_PREVIEW_VOXELS) {
                    throw new IllegalArgumentException("preview voxel count exceeds " + MAX_PREVIEW_VOXELS);
                }
            }
        }
        return List.copyOf(voxels);
    }

    private static void addEntry(Set<Voxel> voxels, SplinePlacementPlanEntry entry, BuilderTransform transform) {
        BuilderVec3 position = transform.transformPoint(entry.position());
        SplineFrame frame = transform.transformFrame(entry.frame());
        add(voxels, position);
        double radius = entry.radius();
        BuilderVec3 normal = frame.normal().multiply(radius);
        BuilderVec3 binormal = frame.binormal().multiply(radius);
        add(voxels, position.add(normal));
        add(voxels, position.subtract(normal));
        add(voxels, position.add(binormal));
        add(voxels, position.subtract(binormal));
    }

    private static void add(Set<Voxel> voxels, BuilderVec3 position) {
        voxels.add(new Voxel(rounded(position.x()), rounded(position.y()), rounded(position.z())));
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
