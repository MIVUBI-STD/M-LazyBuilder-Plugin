package com.halokaryamedia.lazybuilder.builder.net;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;

public record BuilderExtensionCapabilities(
        boolean negotiated,
        int capabilityMask,
        int maxBatchEntries,
        String status
) {
    public BuilderExtensionCapabilities {
        if (maxBatchEntries < 0
                || maxBatchEntries > BuilderExtensionWireProtocol.MAX_BATCH_ENTRIES) {
            throw new IllegalArgumentException("maxBatchEntries out of range");
        }
        status = status == null || status.isBlank() ? "unknown" : status;
    }

    public static BuilderExtensionCapabilities unavailable(String status) {
        return new BuilderExtensionCapabilities(false, 0, 0, status);
    }

    public boolean supportsBiome() {
        return negotiated
                && (capabilityMask
                & BuilderExtensionWireProtocol.CAPABILITY_BIOME) != 0;
    }

    public boolean supportsBlockEntity() {
        return negotiated
                && (capabilityMask
                & BuilderExtensionWireProtocol.CAPABILITY_BLOCK_ENTITY) != 0;
    }

    public boolean supportsEntity() {
        return negotiated
                && (capabilityMask
                & BuilderExtensionWireProtocol.CAPABILITY_ENTITY) != 0;
    }
}
