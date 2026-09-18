package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface BlockEntityPayloadTransform {
    byte[] transform(byte[] payload, StructurePlacement placement);

    static BlockEntityPayloadTransform identity() {
        return (payload, placement) -> payload.clone();
    }
}
