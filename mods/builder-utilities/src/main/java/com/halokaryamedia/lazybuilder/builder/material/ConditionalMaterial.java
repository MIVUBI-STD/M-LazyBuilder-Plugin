package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

public record ConditionalMaterial(
        ScalarField field,
        double threshold,
        BuilderMaterial whenAtOrAbove,
        BuilderMaterial whenBelow
) implements BuilderMaterial {
    public ConditionalMaterial {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(whenAtOrAbove, "whenAtOrAbove");
        Objects.requireNonNull(whenBelow, "whenBelow");
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold must be finite");
        }
    }

    @Override
    public String resolve(MaterialContext context) {
        return (field.sample(context) >= threshold ? whenAtOrAbove : whenBelow).resolve(context);
    }
}
