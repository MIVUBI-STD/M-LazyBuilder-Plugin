package com.halokaryamedia.lazybuilder.builder.mutation;

@FunctionalInterface
public interface WorldBlockStateSource {
    String readBlockState(int worldX, int y, int worldZ);
}
