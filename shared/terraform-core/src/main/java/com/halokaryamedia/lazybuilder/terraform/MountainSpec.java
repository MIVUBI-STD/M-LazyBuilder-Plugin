package com.halokaryamedia.lazybuilder.terraform;

import java.util.Objects;

public record MountainSpec(
        Vec3d origin,
        Vec3d facing,
        double size,
        double height,
        TerrainVariation variation,
        long seed
) {
    public MountainSpec {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(variation, "variation");
        facing = facing.horizontalNormalized();
        if (!Double.isFinite(size) || size <= 0.0) throw new IllegalArgumentException("size must be positive");
        if (!Double.isFinite(height) || height <= 0.0) throw new IllegalArgumentException("height must be positive");
    }
    public MountainShape createField() { return new MountainShape(this); }
}
