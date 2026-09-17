package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.Objects;

public record SplineControlPoint(BuilderVec3 position, double radius, double rollDegrees) {
    public SplineControlPoint {
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(radius) || radius <= 0.0) throw new IllegalArgumentException("radius must be finite and > 0");
        if (!Double.isFinite(rollDegrees)) throw new IllegalArgumentException("rollDegrees must be finite");
    }
}
