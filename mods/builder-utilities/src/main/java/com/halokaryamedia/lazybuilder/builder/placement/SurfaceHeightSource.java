package com.halokaryamedia.lazybuilder.builder.placement;

@FunctionalInterface
public interface SurfaceHeightSource {
    int yAt(int x, int z);
}
