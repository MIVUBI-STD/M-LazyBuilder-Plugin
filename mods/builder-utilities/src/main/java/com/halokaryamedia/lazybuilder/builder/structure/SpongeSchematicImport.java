package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Imported Sponge schematic plus its file-level placement metadata. */
public record SpongeSchematicImport(
        StructureSnapshot snapshot,
        int offsetX,
        int offsetY,
        int offsetZ,
        int dataVersion
) {
    public SpongeSchematicImport {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
