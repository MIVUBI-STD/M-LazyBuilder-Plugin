package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Canonical opaque block-entity payload stored at a structure-local block position. */
public record StructureBlockEntity(int x, int y, int z, byte[] payload) {
    public StructureBlockEntity {
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
