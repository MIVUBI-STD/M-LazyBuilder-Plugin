package com.halokaryamedia.lazybuilder.builder.mutation;

public interface WorldBlockMutationTarget extends WorldBlockStateSource {
    void writeBlockState(int worldX, int y, int worldZ, String blockState);
}
