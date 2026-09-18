package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.region.PackedWorldBlockPosition;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.symmetry.BuilderTransform;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Converts spline placement plans into exact packed mutation voxels and bounded preview voxels. */
public final class SplinePreviewVoxelizer {
    public static final int MAX_PREVIEW_VOXELS = 250_000;
    public static final int MAX_MUTATION_VOXELS = 2_000_000;
    private static final int POINTS_PER_ENTRY = 5;
    private static final int MAX_RAW_VOXELS = 10_000_000;

    private SplinePreviewVoxelizer() {}

    public static List<Voxel> voxelize(List<SplinePlacementPlanEntry> plan) {
        return voxelize(plan, List.of(BuilderTransform.identity()));
    }

    /** Preview-only path. Large exact mutation sets are evenly decimated for rendering. */
    public static List<Voxel> voxelize(
            List<SplinePlacementPlanEntry> plan,
            List<BuilderTransform> transforms
    ) {
        long[] exact = voxelizePacked(plan, transforms, MAX_MUTATION_VOXELS);
        if (exact.length <= MAX_PREVIEW_VOXELS) {
            return Arrays.stream(exact)
                    .mapToObj(SplinePreviewVoxelizer::unpack)
                    .toList();
        }

        Voxel[] preview = new Voxel[MAX_PREVIEW_VOXELS];
        double step = exact.length / (double) MAX_PREVIEW_VOXELS;
        for (int i = 0; i < preview.length; i++) {
            int index = Math.min(exact.length - 1, (int) Math.floor(i * step));
            preview[i] = unpack(exact[index]);
        }
        return List.of(preview);
    }

    public static boolean previewIsDecimated(
            List<SplinePlacementPlanEntry> plan,
            List<BuilderTransform> transforms
    ) {
        long raw = rawCount(plan, transforms);
        return raw > MAX_PREVIEW_VOXELS;
    }

    /** Exact mutation voxels in sorted unique packed form. */
    public static long[] voxelizePacked(
            List<SplinePlacementPlanEntry> plan,
            List<BuilderTransform> transforms,
            int maxUniqueVoxels
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(transforms, "transforms");
        if (transforms.isEmpty()) throw new IllegalArgumentException("transforms must not be empty");
        if (maxUniqueVoxels <= 0) throw new IllegalArgumentException("maxUniqueVoxels must be > 0");

        long rawCount = rawCount(plan, transforms);
        if (rawCount > MAX_RAW_VOXELS) {
            throw new IllegalArgumentException("raw spline voxel workload exceeds " + MAX_RAW_VOXELS);
        }
        long[] raw = new long[Math.toIntExact(rawCount)];
        int cursor = 0;
        for (BuilderTransform transform : transforms) {
            Objects.requireNonNull(transform, "transform");
            for (SplinePlacementPlanEntry entry : plan) {
                Objects.requireNonNull(entry, "entry");
                BuilderVec3 position = transform.transformPoint(entry.position());
                SplineFrame frame = transform.transformFrame(entry.frame());
                raw[cursor++] = pack(position);
                double radius = entry.radius();
                BuilderVec3 normal = frame.normal().multiply(radius);
                BuilderVec3 binormal = frame.binormal().multiply(radius);
                raw[cursor++] = pack(position.add(normal));
                raw[cursor++] = pack(position.subtract(normal));
                raw[cursor++] = pack(position.add(binormal));
                raw[cursor++] = pack(position.subtract(binormal));
            }
        }

        Arrays.sort(raw);
        int unique = raw.length == 0 ? 0 : 1;
        for (int i = 1; i < raw.length; i++) {
            if (raw[i] != raw[unique - 1]) raw[unique++] = raw[i];
        }
        if (unique > maxUniqueVoxels) {
            throw new IllegalArgumentException(
                    "spline mutation voxel count exceeds " + maxUniqueVoxels + ": " + unique);
        }
        return Arrays.copyOf(raw, unique);
    }

    private static long rawCount(
            List<SplinePlacementPlanEntry> plan,
            List<BuilderTransform> transforms
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(transforms, "transforms");
        try {
            return Math.multiplyExact(
                    Math.multiplyExact((long) plan.size(), transforms.size()),
                    POINTS_PER_ENTRY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("spline voxel count overflow", e);
        }
    }

    private static long pack(BuilderVec3 position) {
        return PackedWorldBlockPosition.pack(
                rounded(position.x()), rounded(position.y()), rounded(position.z()));
    }

    private static Voxel unpack(long packed) {
        return new Voxel(
                PackedWorldBlockPosition.x(packed),
                PackedWorldBlockPosition.y(packed),
                PackedWorldBlockPosition.z(packed));
    }

    private static int rounded(double value) {
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("spline coordinate exceeds integer world range");
        }
        return (int) rounded;
    }

    public record Voxel(int x, int y, int z) {}
}
