package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;

import java.io.IOException;

/**
 * Platform authority for opaque History extension payloads.
 *
 * <p>Adapters may implement either the coordinate form or the frame form.
 * Defaults bridge frame calls to coordinates while coordinate defaults fail
 * explicitly when only frame-aware access is meaningful.</p>
 */
public interface HistoryExtensionMutationTarget {
    default byte[] read(String typeId, int chunkX, int chunkZ, long localKey) throws IOException {
        throw new UnsupportedOperationException(
                "coordinate-based extension read is not implemented for " + typeId);
    }

    default void write(
            String typeId,
            int chunkX,
            int chunkZ,
            long localKey,
            byte[] payload
    ) throws IOException {
        throw new UnsupportedOperationException(
                "coordinate-based extension write is not implemented for " + typeId);
    }

    default byte[] read(HistoryExtensionFrame frame) throws IOException {
        return read(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey());
    }

    default void write(HistoryExtensionFrame frame, byte[] payload) throws IOException {
        write(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey(), payload);
    }
}
