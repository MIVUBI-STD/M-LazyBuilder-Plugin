package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates reusable transform sets for reflectional, rotational and translational symmetry.
 */
public final class SymmetryPlanner {
    public static final int MAX_COPIES = 256;

    private SymmetryPlanner() {
    }

    public static List<BuilderTransform> reflection(BuilderVec3 pointOnPlane, BuilderVec3 normal) {
        return List.of(BuilderTransform.identity(), BuilderTransform.reflection(pointOnPlane, normal));
    }

    public static List<BuilderTransform> rotational(BuilderVec3 origin, BuilderVec3 axis, int copies) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(axis, "axis");
        validateCopies(copies);
        List<BuilderTransform> result = new ArrayList<>(copies);
        for (int index = 0; index < copies; index++) {
            result.add(BuilderTransform.rotation(origin, axis, Math.PI * 2.0 * index / copies));
        }
        return List.copyOf(result);
    }

    public static List<BuilderTransform> translational(BuilderVec3 offset, int copies) {
        Objects.requireNonNull(offset, "offset");
        validateCopies(copies);
        if (offset.lengthSquared() == 0.0 && copies > 1) {
            throw new IllegalArgumentException("non-zero translation offset required for multiple copies");
        }
        List<BuilderTransform> result = new ArrayList<>(copies);
        for (int index = 0; index < copies; index++) {
            result.add(BuilderTransform.translation(offset.multiply(index)));
        }
        return List.copyOf(result);
    }

    private static void validateCopies(int copies) {
        if (copies < 1 || copies > MAX_COPIES) {
            throw new IllegalArgumentException("copies must be in [1, " + MAX_COPIES + "]");
        }
    }
}
