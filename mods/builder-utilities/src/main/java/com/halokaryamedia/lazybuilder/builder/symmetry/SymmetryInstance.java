package com.halokaryamedia.lazybuilder.builder.symmetry;

import java.util.Objects;

/**
 * Pairs a source plan item with one symmetry transform. Execution remains external.
 */
public record SymmetryInstance<T>(T source, BuilderTransform transform, int copyIndex) {
    public SymmetryInstance {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transform, "transform");
        if (copyIndex < 0) throw new IllegalArgumentException("copyIndex must be >= 0");
    }
}
