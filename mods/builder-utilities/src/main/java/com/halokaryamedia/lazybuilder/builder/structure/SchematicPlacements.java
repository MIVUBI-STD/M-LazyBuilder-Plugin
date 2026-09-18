package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Applies Sponge file Offset in the same mirror/rotation space as the structure. */
public final class SchematicPlacements {
    private SchematicPlacements() {}

    public static StructurePlacement atPasteBase(
            SpongeSchematicImport imported,
            BlockPos pasteBase,
            int quarterTurns,
            boolean mirrorX,
            boolean mirrorZ
    ) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(pasteBase, "pasteBase");

        StructurePlacement orientation = new StructurePlacement(
                0, 0, 0, quarterTurns, mirrorX, mirrorZ);
        StructurePlacement.WorldPosition transformedOffset = orientation.transform(
                imported.offsetX(), imported.offsetY(), imported.offsetZ());

        return new StructurePlacement(
                Math.addExact(pasteBase.getX(), transformedOffset.x()),
                Math.addExact(pasteBase.getY(), transformedOffset.y()),
                Math.addExact(pasteBase.getZ(), transformedOffset.z()),
                quarterTurns,
                mirrorX,
                mirrorZ
        );
    }
}
