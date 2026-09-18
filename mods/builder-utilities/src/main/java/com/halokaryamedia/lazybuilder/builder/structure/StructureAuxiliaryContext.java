package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Adapter bundle for non-block structure payload planning. */
public record StructureAuxiliaryContext(
        StructureBlockEntityStateSource blockEntities,
        BlockEntityPayloadTransform blockEntityTransform,
        StructureBiomeStateSource biomes,
        BiomePayloadTransform biomeTransform,
        StructureEntityStateSource entities,
        EntityPayloadTransform entityTransform
) {
    public StructureAuxiliaryContext {
        Objects.requireNonNull(blockEntityTransform, "blockEntityTransform");
        Objects.requireNonNull(biomeTransform, "biomeTransform");
        Objects.requireNonNull(entityTransform, "entityTransform");
    }

    public static StructureAuxiliaryContext none() {
        return new StructureAuxiliaryContext(
                null,
                BlockEntityPayloadTransform.identity(),
                null,
                BiomePayloadTransform.identity(),
                null,
                EntityPayloadTransform.identity()
        );
    }
}
