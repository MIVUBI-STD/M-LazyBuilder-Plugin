package com.halokaryamedia.lazybuilder.builder.material;

/**
 * @deprecated Use {@link ScalarFields}; retained as a source-compatible façade.
 */
@Deprecated
public final class FieldMath {
    private FieldMath() {}

    public static ScalarField clamp(ScalarField source, double min, double max) {
        return ScalarFields.clamp(source, min, max);
    }

    public static ScalarField remap(
            ScalarField source,
            double inputMin,
            double inputMax,
            double outputMin,
            double outputMax
    ) {
        return ScalarFields.remap(source, inputMin, inputMax, outputMin, outputMax);
    }

    public static ScalarField multiply(ScalarField a, ScalarField b) {
        return ScalarFields.multiply(a, b);
    }

    public static ScalarField add(ScalarField a, ScalarField b) {
        return ScalarFields.add(a, b);
    }

    public static ScalarField invert01(ScalarField source) {
        return ScalarFields.invert01(source);
    }
}
