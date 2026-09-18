package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.Objects;

/** Typed adapter-facing view that serializes into the opaque core frame format. */
public record TypedHistoryExtension<T>(
        HistoryExtensionType<T> type,
        int chunkX,
        int chunkZ,
        long localKey,
        T before,
        T after
) {
    public TypedHistoryExtension {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    public HistoryExtensionFrame toFrame() throws IOException {
        return new HistoryExtensionFrame(
                type.id(),
                chunkX,
                chunkZ,
                localKey,
                type.codec().encode(before),
                type.codec().encode(after)
        );
    }

    public static <T> TypedHistoryExtension<T> fromFrame(
            HistoryExtensionType<T> type,
            HistoryExtensionFrame frame
    ) throws IOException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(frame, "frame");
        if (!type.id().equals(frame.typeId())) {
            throw new IllegalArgumentException(
                    "frame type " + frame.typeId() + " does not match " + type.id());
        }
        return new TypedHistoryExtension<>(
                type,
                frame.chunkX(),
                frame.chunkZ(),
                frame.localKey(),
                type.codec().decode(frame.beforePayload()),
                type.codec().decode(frame.afterPayload())
        );
    }
}
