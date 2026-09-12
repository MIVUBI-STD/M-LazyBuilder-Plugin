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
        if (chunkBytes < 1) throw new IllegalArgumentException("chunkBytes must be positive");
        if (totalChunks < 0) throw new IllegalArgumentException("totalChunks must not be negative");
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be hexadecimal");
    }
}
