package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;
import java.util.Optional;

/**
 * Selects the storage class for a history stream from a preflight byte estimate.
 * Actual storage implementations are deliberately separate from this policy.
 */
public record HistorySizingPolicy(long maxMemoryBytes, long maxCompressedMemoryBytes) {
    public HistorySizingPolicy {
        if (maxMemoryBytes <= 0) {
            throw new IllegalArgumentException("maxMemoryBytes must be > 0");
        }
        if (maxCompressedMemoryBytes < maxMemoryBytes) {
            throw new IllegalArgumentException("maxCompressedMemoryBytes must be >= maxMemoryBytes");
        }
    }

    public Optional<HistoryStorageTier> select(HistoryRequirement requirement, long estimatedBytes) {
        Objects.requireNonNull(requirement, "requirement");
        if (estimatedBytes < 0) {
            throw new IllegalArgumentException("estimatedBytes must be >= 0");
        }
        if (requirement == HistoryRequirement.NONE) {
            return Optional.empty();
        }
        if (estimatedBytes <= maxMemoryBytes) {
            return Optional.of(HistoryStorageTier.MEMORY);
        }
        if (estimatedBytes <= maxCompressedMemoryBytes) {
            return Optional.of(HistoryStorageTier.COMPRESSED_MEMORY);
        }
        return Optional.of(HistoryStorageTier.DISK);
    }
}
