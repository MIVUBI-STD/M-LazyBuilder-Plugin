package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

public record AxisGradientField(Axis axis, double min, double max) implements ScalarField {
    public enum Axis { X, Y, Z }

    public AxisGradientField {
        Objects.requireNonNull(axis, "axis");
        if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
            throw new IllegalArgumentException("gradient requires finite max > min");
        }
    }

    @Override
    public double sample(MaterialContext context) {
        double coordinate = switch (axis) {
            case X -> context.x();
            case Y -> context.y();
            case Z -> context.z();
        };
        double value = (coordinate - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, value));
    }
}
