package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

/** Adapter-owned deterministic encoding for one History extension payload type. */
public interface HistoryExtensionCodec<T> {
    byte[] encode(T value) throws IOException;

    T decode(byte[] payload) throws IOException;
}
