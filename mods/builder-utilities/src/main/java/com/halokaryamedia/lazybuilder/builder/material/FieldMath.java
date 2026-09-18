package com.halokaryamedia.lazybuilder.builder.material;

/** Pure scalar-field composition helpers shared by procedural materials. */
public final class FieldMath {
    private FieldMath() {}

    public static ScalarField clamp(ScalarField source, double min, double max) {
        if (source == null) throw new NullPointerException("source");
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("invalid clamp range");
        }
        return context -> Math.max(min, Math.min(max, source.sample(context)));
    }

    public static ScalarField remap(
            ScalarField source,
            double inputMin,
            double inputMax,
            double outputMin,
            double outputMax
    ) {
        if (source == null) throw new NullPointerException("source");
        if (!Double.isFinite(inputMin) || !Double.isFinite(inputMax) || inputMax <= inputMin
                || !Double.isFinite(outputMin) || !Double.isFinite(outputMax)) {
            throw new IllegalArgumentException("invalid remap range");
        }
        return context -> {
            double t = (source.sample(context) - inputMin) / (inputMax - inputMin);
            t = Math.max(0.0, Math.min(1.0, t));
            return outputMin + (outputMax - outputMin) * t;
        };
    }

    public static ScalarField multiply(ScalarField a, ScalarField b) {
        if (a == null || b == null) throw new NullPointerException("field");
        return context -> a.sample(context) * b.sample(context);
    }

    public static ScalarField add(ScalarField a, ScalarField b) {
        if (a == null || b == null) throw new NullPointerException("field");
        return context -> a.sample(context) + b.sample(context);
    }

    public static ScalarField invert01(ScalarField source) {
        if (source == null) throw new NullPointerException("source");
        return context -> 1.0 - Math.max(0.0, Math.min(1.0, source.sample(context)));
    }
}
