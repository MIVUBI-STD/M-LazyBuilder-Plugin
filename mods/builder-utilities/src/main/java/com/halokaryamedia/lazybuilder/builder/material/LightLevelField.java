package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/** Normalized light level field for procedural weathering/exposure materials. */
public final class LightLevelField implements ScalarField {
    private final LightFieldSource source;

    public LightLevelField(LightFieldSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public double sample(MaterialContext context) {
        int value = source.lightLevelAt(context.x(), context.y(), context.z());
        if (value < 0 || value > 15) {
            throw new IllegalArgumentException("light level must be in 0..15");
        }
        return value / 15.0;
    }
}
