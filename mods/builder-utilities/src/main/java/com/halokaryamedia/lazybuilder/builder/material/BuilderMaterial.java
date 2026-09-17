package com.halokaryamedia.lazybuilder.builder.material;

/** Resolves the block state to place for one world position. */
@FunctionalInterface
public interface BuilderMaterial {
    String resolve(MaterialContext context);
}
