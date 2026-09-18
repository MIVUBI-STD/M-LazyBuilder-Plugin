package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;

import java.util.List;
import java.util.Objects;

/**
 * Places reusable blueprint/structure sources at stable arc-length intervals.
 */
public record StructureChainSplinePayload(
        double spacing,
        PlacementSource source,
        PlacementVariation variation
) implements SplinePayload<SplinePlacementPlanEntry> {
    public StructureChainSplinePayload {
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException("spacing must be finite and > 0");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variation, "variation");
    }

    @Override
    public List<SplinePlacementPlanEntry> plan(List<SplineSample> samples, OperationSeed seed) {
        return SplinePlacementPlanner.plan(samples, spacing, source, variation, seed);
    }
}
