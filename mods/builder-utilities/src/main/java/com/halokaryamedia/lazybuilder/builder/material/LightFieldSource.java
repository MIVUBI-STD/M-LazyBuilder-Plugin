package com.halokaryamedia.lazybuilder.builder.material;

@FunctionalInterface
public interface LightFieldSource {
    int lightLevelAt(int x, int y, int z);
}
