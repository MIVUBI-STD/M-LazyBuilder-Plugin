package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface BlockStateTransform {
    String transform(String blockState, StructurePlacement placement);

    static BlockStateTransform identity() {
        return (state, placement) -> state;
    }
}
