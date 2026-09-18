package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.BlockEntityPayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;

public final class AxiomBlockEntityPayloadTransform implements BlockEntityPayloadTransform {
    @Override
    public byte[] transform(byte[] payload, StructurePlacement placement) {
        try {
            return AxiomBlockEntityPayloads.canonicalize(payload);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid block entity NBT payload", e);
        }
    }
}
