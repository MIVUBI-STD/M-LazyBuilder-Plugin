package com.halokaryamedia.lazybuilder.builder.material;

/** Leaves the current block state unchanged when used as a material branch. */
public enum ExistingBlockMaterial implements BuilderMaterial {
    INSTANCE;

    @Override
    public String resolve(MaterialContext context) {
        return context.existingBlockState();
    }
}
