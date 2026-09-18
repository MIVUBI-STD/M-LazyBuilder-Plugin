package com.halokaryamedia.lazybuilder.builder.structure;

public record StructureBlock(int x, int y, int z, String blockState) {
    public StructureBlock {
        if (blockState == null || blockState.isBlank()) {
            throw new IllegalArgumentException("blockState must be non-blank");
        }
    }
}
