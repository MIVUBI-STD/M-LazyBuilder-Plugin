package com.halokaryamedia.lazybuilder.terraform;

import java.util.Objects;

public record RidgeSpec(TerrainPath path, double size, double height, TerrainVariation variation, long seed) {
    public RidgeSpec {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(variation, "variation");
        if (!Double.isFinite(size) || size <= 0.0) throw new IllegalArgumentException("size must be positive");
        if (!Double.isFinite(height) || height <= 0.0) throw new IllegalArgumentException("height must be positive");
    }
    public RidgeShape createField() { return new RidgeShape(this); }
}
