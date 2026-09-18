package com.halokaryamedia.lazybuilder.builder.placement;

@FunctionalInterface
public interface PlacementBiomeSource {
    String biomeAt(int x, int y, int z);
}
