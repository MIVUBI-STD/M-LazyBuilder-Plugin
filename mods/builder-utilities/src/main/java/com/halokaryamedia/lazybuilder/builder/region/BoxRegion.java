package com.halokaryamedia.lazybuilder.builder.region;

import java.util.Objects;

public record BoxRegion(BlockBounds bounds) implements BuilderRegion {
    public BoxRegion {
        Objects.requireNonNull(bounds, "bounds");
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }
}
