package com.halokaryamedia.lazybuilder.builder.placement;

public record PlacementTransform(double yawDegrees, double scale, boolean mirrorX) {
    public PlacementTransform {
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and > 0");
        }
    }
}
