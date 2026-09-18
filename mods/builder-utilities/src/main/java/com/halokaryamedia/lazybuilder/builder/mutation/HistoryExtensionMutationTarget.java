package com.halokaryamedia.lazybuilder.builder.mutation;

import java.io.IOException;

/** Platform authority for reading/writing opaque History extension payloads. */
public interface HistoryExtensionMutationTarget {
    byte[] read(String typeId, int chunkX, int chunkZ, long localKey) throws IOException;

    void write(String typeId, int chunkX, int chunkZ, long localKey, byte[] payload) throws IOException;
}
