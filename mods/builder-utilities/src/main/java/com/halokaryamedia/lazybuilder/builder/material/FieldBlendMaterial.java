package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/** Deterministically blends two materials using a scalar field as probability. */
public record FieldBlendMaterial(
        ScalarField blend,
        BuilderMaterial low,
        BuilderMaterial high,
        long channel
) implements BuilderMaterial {
    public FieldBlendMaterial {
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
    }

    @Override
    public String resolve(MaterialContext context) {
        double probability = Math.max(0.0, Math.min(1.0, blend.sample(context)));
        double sample = context.seed().sampleUnit(context.x(), context.y(), context.z(), channel);
        return (sample < probability ? high : low).resolve(context);
    }
}
