package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/** Converts a normalized source field into sharp ridges while remaining deterministic. */
public final class RidgedNoiseField implements ScalarField {
    private final ScalarField source;
    private final double sharpness;

    public RidgedNoiseField(ScalarField source, double sharpness) {
        this.source = Objects.requireNonNull(source, "source");
        if (!Double.isFinite(sharpness) || sharpness <= 0.0) {
            throw new IllegalArgumentException("sharpness must be finite and > 0");
        }
        this.sharpness = sharpness;
    }

    @Override
    public double sample(MaterialContext context) {
        double sourceValue = Math.max(0.0, Math.min(1.0, source.sample(context)));
        double ridge = 1.0 - Math.abs(sourceValue * 2.0 - 1.0);
        return Math.pow(ridge, sharpness);
    }
}
