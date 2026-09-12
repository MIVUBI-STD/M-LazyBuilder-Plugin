package com.halokaryamedia.lazybuilder.world.transfer;

import java.time.Duration;
import java.util.Objects;

/** Bounded defaults for request-driven client/server world-file transfer. */
public record TransferPolicy(
        int chunkBytes,
        long maxUploadBytes,
        int maxConcurrentUploads,
        int maxConcurrentDownloads,
        Duration idleTimeout
) {
    public TransferPolicy {
        if (chunkBytes < 1 || chunkBytes > 1024 * 1024) {
            throw new IllegalArgumentException("chunkBytes must be between 1 byte and 1 MiB");
        }
        if (maxUploadBytes < 1) throw new IllegalArgumentException("maxUploadBytes must be positive");
        if (maxConcurrentUploads < 1) throw new IllegalArgumentException("maxConcurrentUploads must be positive");
        if (maxConcurrentDownloads < 1) throw new IllegalArgumentException("maxConcurrentDownloads must be positive");
        idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
    }

    /** Backward-compatible constructor for tests/callers that use the default timeout. */
    public TransferPolicy(int chunkBytes, long maxUploadBytes, int maxConcurrentUploads, int maxConcurrentDownloads) {
        this(chunkBytes, maxUploadBytes, maxConcurrentUploads, maxConcurrentDownloads, Duration.ofMinutes(5));
    }

    public static TransferPolicy defaults() {
        return new TransferPolicy(24 * 1024, 16L * 1024L * 1024L * 1024L, 1, 2, Duration.ofMinutes(5));
    }
}
