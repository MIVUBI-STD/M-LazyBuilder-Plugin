package com.halokaryamedia.lazybuilder.terraform;

import java.util.Objects;

/** Full path-based Cliff intent. Front is the exposed side of the cliff. */
public record CliffPathSpec(
        TerrainPath path,
        Vec3d front,
        double size,
        double height,
        TerrainVariation variation,
        long seed
) {
    public CliffPathSpec {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(front, "front");
        Objects.requireNonNull(variation, "variation");
        front = front.horizontalNormalized();
        if (!Double.isFinite(size) || size <= 0.0) throw new IllegalArgumentException("size must be positive");
        if (!Double.isFinite(height) || height <= 0.0) throw new IllegalArgumentException("height must be positive");
    }

    public CliffPathShape createField() { return new CliffPathShape(this); }
}
