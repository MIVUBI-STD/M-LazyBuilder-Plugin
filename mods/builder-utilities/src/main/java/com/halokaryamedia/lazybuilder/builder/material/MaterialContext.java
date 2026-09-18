package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import java.util.Objects;

public record MaterialContext(int x, int y, int z, String existingBlockState, OperationSeed seed) {
    public MaterialContext {
        if (existingBlockState == null || existingBlockState.isBlank()) {
            throw new IllegalArgumentException("existingBlockState must be non-blank");
        }
        Objects.requireNonNull(seed, "seed");
    }
}
