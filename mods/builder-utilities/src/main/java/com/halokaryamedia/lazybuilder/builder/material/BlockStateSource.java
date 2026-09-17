package com.halokaryamedia.lazybuilder.builder.material;

@FunctionalInterface
public interface BlockStateSource {
    String stateAt(int x, int y, int z);
}
