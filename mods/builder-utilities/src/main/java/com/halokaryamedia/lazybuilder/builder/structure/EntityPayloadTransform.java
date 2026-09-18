package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface EntityPayloadTransform {
    byte[] transform(byte[] payload, StructurePlacement placement);

    static EntityPayloadTransform identity() {
        return (payload, placement) -> payload.clone();
    }
}
