package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;

/**
 * Opaque non-block history payload owned by a platform adapter.
 *
 * <p>The core intentionally does not know NBT, biome, or entity types. A future
 * adapter may use namespaced type ids such as lazybuilder:block_entity,
 * lazybuilder:biome, or lazybuilder:entity and encode canonical before/after
 * payloads.</p>
 */
public record HistoryExtensionFrame(
        String typeId,
        int chunkX,
        int chunkZ,
        long localKey,
        byte[] beforePayload,
        byte[] afterPayload
) {
    public HistoryExtensionFrame {
        if (typeId == null || typeId.isBlank()) {
            throw new IllegalArgumentException("typeId must be non-blank");
        }
        Objects.requireNonNull(beforePayload, "beforePayload");
        Objects.requireNonNull(afterPayload, "afterPayload");
        beforePayload = beforePayload.clone();
        afterPayload = afterPayload.clone();
    }

    @Override
    public byte[] beforePayload() {
        return beforePayload.clone();
    }

    @Override
    public byte[] afterPayload() {
        return afterPayload.clone();
    }

    public byte[] payload(ReplayDirection direction) {
        return direction == ReplayDirection.UNDO ? beforePayload() : afterPayload();
    }
}
