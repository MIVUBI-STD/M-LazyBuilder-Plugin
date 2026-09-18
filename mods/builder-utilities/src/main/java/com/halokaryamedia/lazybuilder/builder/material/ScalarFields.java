package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/** Small composable scalar-field operators shared by procedural materials and masks. */
public final class ScalarFields {
    private ScalarFields() {}

    public static ScalarField clamp01(ScalarField source) {
        Objects.requireNonNull(source, "source");
        return context -> clamp(source.sample(context), 0.0, 1.0);
    }

    public static ScalarField invert01(ScalarField source) {
        Objects.requireNonNull(source, "source");
        return context -> 1.0 - clamp(source.sample(context), 0.0, 1.0);
    }

    public static ScalarField multiply(ScalarField a, ScalarField b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return context -> a.sample(context) * b.sample(context);
    }

    public static ScalarField add(ScalarField a, ScalarField b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return context -> a.sample(context) + b.sample(context);
    }

    public static ScalarField min(ScalarField a, ScalarField b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return context -> Math.min(a.sample(context), b.sample(context));
    }

    public static ScalarField max(ScalarField a, ScalarField b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return context -> Math.max(a.sample(context), b.sample(context));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
