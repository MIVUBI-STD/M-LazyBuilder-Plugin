package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface BiomePayloadTransform {
    byte[] transform(byte[] payload, StructurePlacement placement);

    static BiomePayloadTransform identity() {
        return (payload, placement) -> payload.clone();
    }
}
