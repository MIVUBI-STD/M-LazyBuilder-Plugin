package com.halokaryamedia.lazybuilder.builder.placement;

public record PlacementPoint(int x, int y, int z, int ordinal) {
    public PlacementPoint {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
    }
}
