package com.halokaryamedia.lazybuilder.builder.symmetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic replication layer. It does not know how the underlying item mutates the world.
 */
public final class SymmetryReplicator {
    public static final int MAX_INSTANCES = 1_000_000;

    private SymmetryReplicator() {
    }

    public static <T> List<SymmetryInstance<T>> replicate(List<T> source, List<BuilderTransform> transforms) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transforms, "transforms");
        long count = Math.multiplyExact((long) source.size(), transforms.size());
        if (count > MAX_INSTANCES) {
            throw new IllegalArgumentException("symmetry instance count exceeds " + MAX_INSTANCES);
        }
        List<SymmetryInstance<T>> result = new ArrayList<>((int) count);
        for (int copyIndex = 0; copyIndex < transforms.size(); copyIndex++) {
            BuilderTransform transform = Objects.requireNonNull(transforms.get(copyIndex), "transform");
            for (T item : source) {
                result.add(new SymmetryInstance<>(Objects.requireNonNull(item, "source item"), transform, copyIndex));
            }
        }
        return List.copyOf(result);
    }
}
