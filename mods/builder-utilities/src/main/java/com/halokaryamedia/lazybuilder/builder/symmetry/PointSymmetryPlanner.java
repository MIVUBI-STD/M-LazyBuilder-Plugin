package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Integer-safe symmetry replication for point-based placement plans. */
public final class PointSymmetryPlanner {
    private PointSymmetryPlanner() {}

    public static List<PlacementPoint> rotational(
            List<PlacementPoint> source,
            BuilderVec3 pivot,
            int copies,
            int maxPoints
    ) {
        return rotationalAndMirrors(source, pivot, copies, false, false, maxPoints);
    }

    public static List<PlacementPoint> rotationalAndMirrors(
            List<PlacementPoint> source,
            BuilderVec3 pivot,
            int copies,
            boolean mirrorX,
            boolean mirrorZ,
            int maxPoints
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pivot, "pivot");
        if (copies <= 0) throw new IllegalArgumentException("copies must be > 0");
        if (maxPoints <= 0) throw new IllegalArgumentException("maxPoints must be > 0");

        List<BuilderTransform> rotations = SymmetryPlanner.rotational(
                pivot, new BuilderVec3(0, 1, 0), copies);
        LinkedHashMap<PointKey, PlacementPoint> unique = new LinkedHashMap<>();

        for (BuilderTransform rotation : rotations) {
            for (PlacementPoint point : source) {
                Objects.requireNonNull(point, "point");
                BuilderVec3 rotated = rotation.transformPoint(
                        new BuilderVec3(point.x(), point.y(), point.z()));
                add(unique, rotated.x(), rotated.y(), rotated.z(), maxPoints);
                if (mirrorX) {
                    add(unique, 2.0 * pivot.x() - rotated.x(), rotated.y(), rotated.z(), maxPoints);
                }
                if (mirrorZ) {
                    add(unique, rotated.x(), rotated.y(), 2.0 * pivot.z() - rotated.z(), maxPoints);
                }
                if (mirrorX && mirrorZ) {
                    add(unique,
                            2.0 * pivot.x() - rotated.x(),
                            rotated.y(),
                            2.0 * pivot.z() - rotated.z(),
                            maxPoints);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static void add(
            LinkedHashMap<PointKey, PlacementPoint> unique,
            double xValue,
            double yValue,
            double zValue,
            int maxPoints
    ) {
        int x = rounded(xValue);
        int y = rounded(yValue);
        int z = rounded(zValue);
        PointKey key = new PointKey(x, y, z);
        if (unique.containsKey(key)) return;
        if (unique.size() >= maxPoints) {
            throw new IllegalArgumentException("symmetry point count exceeds " + maxPoints);
        }
        unique.put(key, new PlacementPoint(x, y, z, unique.size()));
    }

    private static int rounded(double value) {
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("symmetry coordinate exceeds integer world range");
        }
        return (int) rounded;
    }

    private record PointKey(int x, int y, int z) {}
}
