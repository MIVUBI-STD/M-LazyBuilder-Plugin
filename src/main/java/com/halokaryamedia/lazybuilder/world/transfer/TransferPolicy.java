package com.halokaryamedia.lazybuilder.world.transfer;

/** Bounded defaults for request-driven client/server world-file transfer. */
public record TransferPolicy(
        int chunkBytes,
        long maxUploadBytes,
        int maxConcurrentUploads,
        int maxConcurrentDownloads
) {
    public TransferPolicy {
        if (chunkBytes < 1024 || chunkBytes > 1024 * 1024) {
            throw new IllegalArgumentException("chunkBytes must be between 1 KiB and 1 MiB");
        }
        if (maxUploadBytes < 1) throw new IllegalArgumentException("maxUploadBytes must be positive");
        if (maxConcurrentUploads < 1) throw new IllegalArgumentException("maxConcurrentUploads must be positive");
        if (maxConcurrentDownloads < 1) throw new IllegalArgumentException("maxConcurrentDownloads must be positive");
    }

    public static TransferPolicy defaults() {
        return new TransferPolicy(24 * 1024, 16L * 1024L * 1024L * 1024L, 1, 2);
    }
}
