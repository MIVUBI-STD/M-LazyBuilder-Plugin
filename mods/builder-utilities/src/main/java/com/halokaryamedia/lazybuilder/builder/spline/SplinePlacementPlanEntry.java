package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;

import java.util.Objects;

/**
 * Immutable structure-placement instruction sampled along a spline.
 */
public record SplinePlacementPlanEntry(
        int ordinal,
        BuilderVec3 position,
        SplineFrame frame,
        double radius,
        String sourceId,
        PlacementTransform variation
) {
    public SplinePlacementPlanEntry {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(frame, "frame");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and > 0");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must be non-blank");
        }
        Objects.requireNonNull(variation, "variation");
    }
}
