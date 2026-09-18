package com.halokaryamedia.lazybuilder.builder.structure;

import java.util.Objects;

/** Imported Sponge schematic plus its file-level placement metadata. */
public record SpongeSchematicImport(
        StructureSnapshot snapshot,
        int offsetX,
        int offsetY,
        int offsetZ,
        int dataVersion,
        byte[] metadataPayload
) {
    public SpongeSchematicImport(
            StructureSnapshot snapshot,
            int offsetX,
            int offsetY,
            int offsetZ,
            int dataVersion
    ) {
        this(snapshot, offsetX, offsetY, offsetZ, dataVersion, new byte[0]);
    }

    public SpongeSchematicImport {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(metadataPayload, "metadataPayload");
        metadataPayload = metadataPayload.clone();
    }

    @Override
    public byte[] metadataPayload() {
        return metadataPayload.clone();
    }

    public boolean hasMetadata() {
        return metadataPayload.length != 0;
    }
}
