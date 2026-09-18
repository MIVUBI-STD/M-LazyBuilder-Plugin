package com.halokaryamedia.lazybuilder.world.transfer;

import java.util.Objects;
import java.util.UUID;

/** Immutable metadata returned to the client when a transfer session starts. */
public record TransferDescriptor(
        UUID sessionId,
        String fileName,
        long totalBytes,
        int chunkBytes,
        int totalChunks,
        String sha256
) {
    public TransferDescriptor {
        Objects.requireNonNull(sessionId, "sessionId");
        fileName = Objects.requireNonNull(fileName, "fileName");
        sha256 = Objects.requireNonNull(sha256, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (totalBytes < 0) throw new IllegalArgumentException("totalBytes must not be negative");
        if (chunkBytes < 1 || chunkBytes > TransferWireProtocol.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("chunkBytes is outside the transfer protocol limit");
        }
        if (totalChunks < 0) throw new IllegalArgumentException("totalChunks must not be negative");
        long expectedChunks = totalBytes == 0 ? 0 : ((totalBytes - 1L) / chunkBytes) + 1L;
        if (expectedChunks > Integer.MAX_VALUE || totalChunks != (int) expectedChunks) {
            throw new IllegalArgumentException("totalChunks does not match totalBytes/chunkBytes");
        }
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be hexadecimal");
    }
}
