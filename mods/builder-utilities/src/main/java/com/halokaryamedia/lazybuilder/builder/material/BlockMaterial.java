package com.halokaryamedia.lazybuilder.builder.material;

public record BlockMaterial(String blockState) implements BuilderMaterial {
    public BlockMaterial {
        if (blockState == null || blockState.isBlank()) {
            throw new IllegalArgumentException("blockState must be non-blank");
        }
    }

    @Override
    public String resolve(MaterialContext context) {
        return blockState;
    }
}
