package com.halokaryamedia.lazybuilder.builder.placement;

import java.util.Objects;
import java.util.function.Predicate;

/** Reusable deterministic filters for placement anchors. */
public final class PlacementConstraints {
    private PlacementConstraints() {}

    public static PlacementConstraint all() {
        return point -> true;
    }

    public static PlacementConstraint slope(
            SurfaceHeightSource surface,
            PlacementFootprint footprint,
            int maxHeightDelta
    ) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(footprint, "footprint");
        if (maxHeightDelta < 0) throw new IllegalArgumentException("maxHeightDelta must be >= 0");

        return point -> {
            int minX = footprint.minX(point);
            int maxX = footprint.maxX(point);
            int minZ = footprint.minZ(point);
            int maxZ = footprint.maxZ(point);

            int h0 = surface.yAt(minX, minZ);
            int h1 = surface.yAt(minX, maxZ);
            int h2 = surface.yAt(maxX, minZ);
            int h3 = surface.yAt(maxX, maxZ);
            int hc = surface.yAt(point.x(), point.z());

            int min = Math.min(hc, Math.min(Math.min(h0, h1), Math.min(h2, h3)));
            int max = Math.max(hc, Math.max(Math.max(h0, h1), Math.max(h2, h3)));
            return (long) max - min <= maxHeightDelta;
        };
    }

    public static PlacementConstraint predicate(Predicate<PlacementPoint> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }

    public static PlacementConstraint not(PlacementConstraint constraint) {
        Objects.requireNonNull(constraint, "constraint");
        return point -> !constraint.test(point);
    }

    public static PlacementConstraint and(PlacementConstraint... constraints) {
        Objects.requireNonNull(constraints, "constraints");
        PlacementConstraint[] copy = constraints.clone();
        for (PlacementConstraint constraint : copy) Objects.requireNonNull(constraint, "constraint");
        return point -> {
            for (PlacementConstraint constraint : copy) {
                if (!constraint.test(point)) return false;
            }
            return true;
        };
    }
}
