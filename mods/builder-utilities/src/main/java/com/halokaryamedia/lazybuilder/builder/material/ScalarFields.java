package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/** Small composable scalar-field operators shared by procedural materials and masks. */
public final class ScalarFields {
    private ScalarFields() {}

    public static ScalarField clamp01(ScalarField source) {
        return clamp(source, 0.0, 1.0);
    }

    public static ScalarField clamp(ScalarField source, double minimum, double maximum) {
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
            throw new IllegalArgumentException("clamp range must be finite and ordered");
        }
        return context -> clamp(source.sample(context), minimum, maximum);
    }

    public static ScalarField remap(
            ScalarField source,
            double inputMinimum,
            double inputMaximum,
            double outputMinimum,
            double outputMaximum
    ) {
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(inputMinimum) || !Double.isFinite(inputMaximum)
                || inputMaximum <= inputMinimum
                || !Double.isFinite(outputMinimum) || !Double.isFinite(outputMaximum)) {
            throw new IllegalArgumentException("invalid remap range");
        }
        return context -> {
            double t = (source.sample(context) - inputMinimum) / (inputMaximum - inputMinimum);
            t = clamp(t, 0.0, 1.0);
            return outputMinimum + (outputMaximum - outputMinimum) * t;
        };
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
