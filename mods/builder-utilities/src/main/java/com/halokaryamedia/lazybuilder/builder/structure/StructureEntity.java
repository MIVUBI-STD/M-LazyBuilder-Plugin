package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Opaque entity payload attached to a structure-local floating-point position. */
public record StructureEntity(double x, double y, double z, byte[] payload) {
    public StructureEntity {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("entity coordinates must be finite");
        }
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
