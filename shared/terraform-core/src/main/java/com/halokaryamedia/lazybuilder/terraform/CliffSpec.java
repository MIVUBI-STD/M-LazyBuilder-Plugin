package com.halokaryamedia.lazybuilder.terraform;

/**
 * Minimal user intent required to construct one cliff.
 * Direction is normalized during construction so callers do not need to do it.
 */
public record CliffSpec(
        double originX,
        double originY,
        double originZ,
        double directionX,
        double directionZ,
        double length,
        double height,
        double width,
        long seed
) {
    public CliffSpec {
        requireFinite(originX, "originX");
        requireFinite(originY, "originY");
        requireFinite(originZ, "originZ");
        requireFinite(directionX, "directionX");
        requireFinite(directionZ, "directionZ");
        requirePositive(length, "length");
        requirePositive(height, "height");
        requirePositive(width, "width");

        double magnitude = Math.hypot(directionX, directionZ);
        if (magnitude < 1.0e-9) {
            throw new IllegalArgumentException("horizontal direction must not be zero");
        }
        directionX /= magnitude;
        directionZ /= magnitude;
    }

    public CliffShape createField() {
        return new CliffShape(this);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requirePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
