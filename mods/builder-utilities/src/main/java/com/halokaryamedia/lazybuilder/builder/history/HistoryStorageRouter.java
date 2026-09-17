package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Selects the concrete storage backend from the preflight sizing policy. */
public final class HistoryStorageRouter {
    private final HistorySizingPolicy sizingPolicy;
    private final Map<HistoryStorageTier, ChangeSetStorage> storageByTier;

    public HistoryStorageRouter(HistorySizingPolicy sizingPolicy, ChangeSetStorage... storages) {
        this.sizingPolicy = Objects.requireNonNull(sizingPolicy, "sizingPolicy");
        EnumMap<HistoryStorageTier, ChangeSetStorage> mapped = new EnumMap<>(HistoryStorageTier.class);
        for (ChangeSetStorage storage : storages) {
            Objects.requireNonNull(storage, "storage");
            ChangeSetStorage previous = mapped.put(storage.tier(), storage);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate History storage tier: " + storage.tier());
            }
        }
        this.storageByTier = Map.copyOf(mapped);
    }

    public Optional<ChangeSetWriter> begin(
            HistoryRequirement requirement,
            long estimatedBytes,
            String operationId
    ) throws IOException {
        Optional<HistoryStorageTier> tier = sizingPolicy.select(requirement, estimatedBytes);
        if (tier.isEmpty()) {
            return Optional.empty();
        }
        ChangeSetStorage storage = storageByTier.get(tier.get());
        if (storage == null) {
            throw new IllegalStateException("No History storage configured for tier " + tier.get());
        }
        return Optional.of(storage.begin(operationId));
    }
}
