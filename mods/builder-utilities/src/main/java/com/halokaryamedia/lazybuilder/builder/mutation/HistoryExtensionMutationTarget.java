package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;

import java.io.IOException;

/** Platform authority for reading/writing opaque History extension payloads. */
public interface HistoryExtensionMutationTarget {
    byte[] read(String typeId, int chunkX, int chunkZ, long localKey) throws IOException;

    void write(String typeId, int chunkX, int chunkZ, long localKey, byte[] payload) throws IOException;

    default byte[] read(HistoryExtensionFrame frame) throws IOException {
        return read(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey());
    }

    default void write(HistoryExtensionFrame frame, byte[] payload) throws IOException {
        write(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey(), payload);
    }
}
