package com.halokaryamedia.lazybuilder.builder.material;

/** Converts normalized noise into sharp deterministic ridges in [0,1]. */
public final class RidgedNoiseField implements ScalarField {
    private final ScalarField source;
    private final double sharpness;

    public RidgedNoiseField(ScalarField source, double sharpness) {
        if (source == null) throw new NullPointerException("source");
        if (!Double.isFinite(sharpness) || sharpness <= 0.0) {
            throw new IllegalArgumentException("sharpness must be finite and > 0");
        }
        this.source = source;
        this.sharpness = sharpness;
    }

    @Override
    public double sample(MaterialContext context) {
        double normalized = Math.max(0.0, Math.min(1.0, source.sample(context)));
        double ridge = 1.0 - Math.abs(normalized * 2.0 - 1.0);
        return Math.pow(ridge, sharpness);
    }
}
