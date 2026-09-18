package com.halokaryamedia.lazybuilder.builder.placement;

/**
 * Deterministic placement orientation/scale. mirrorZ was added after the original
 * contract; the three-argument constructor remains source-compatible and defaults it off.
 */
public record PlacementTransform(
        double yawDegrees,
        double scale,
        boolean mirrorX,
        boolean mirrorZ
) {
    public PlacementTransform(double yawDegrees, double scale, boolean mirrorX) {
        this(yawDegrees, scale, mirrorX, false);
    }

    public PlacementTransform {
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and > 0");
        }
    }
}
