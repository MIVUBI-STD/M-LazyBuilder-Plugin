package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;

public record HistoryExtensionType<T>(
        String id,
        HistoryExtensionCodec<T> codec
) {
    public HistoryExtensionType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        Objects.requireNonNull(codec, "codec");
    }
}
