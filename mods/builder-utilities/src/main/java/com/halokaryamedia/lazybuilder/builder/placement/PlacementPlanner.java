package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.List;
import java.util.Objects;

public final class PlacementPlanner {
    private PlacementPlanner() {}

    public static List<PlacementPlanEntry> plan(
            BlockBounds bounds,
            SurfaceHeightSource surface,
            OperationSeed seed,
            PlacementDistribution distribution,
            PlacementSource source,
            PlacementVariation variation
    ) {
        return plan(bounds, surface, seed, distribution, source, variation,
                PlacementConstraint.all(), PlacementFootprint.point(), false);
    }

    public static List<PlacementPlanEntry> plan(
            BlockBounds bounds,
            SurfaceHeightSource surface,
            OperationSeed seed,
            PlacementDistribution distribution,
            PlacementSource source,
            PlacementVariation variation,
            PlacementConstraint constraint,
            PlacementFootprint footprint,
            boolean rejectFootprintCollisions
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(distribution, "distribution");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variation, "variation");
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(footprint, "footprint");

        List<PlacementPoint> points = distribution.generate(bounds, surface, seed).stream()
                .filter(constraint::test)
                .toList();
        if (rejectFootprintCollisions) {
            points = FootprintCollisionFilter.filter(points, footprint);
        }

        return points.stream()
                .map(point -> new PlacementPlanEntry(
                        point,
                        source.resolve(point, seed),
                        variation.resolve(point, seed)))
                .toList();
    }
}
