package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Opaque biome payload attached to a structure-local sample coordinate. */
public record StructureBiomeSample(int x, int y, int z, byte[] payload) {
    public StructureBiomeSample {
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
