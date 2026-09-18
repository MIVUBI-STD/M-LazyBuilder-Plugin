package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/**
 * Creates a durable reverse plan for only the subset of a cancelled operation
 * that is actually present in world state.
 */
public final class RollbackCancellationPreparer {
    private RollbackCancellationPreparer() {
    }

    public static PreparedRollback prepare(
            StoredChangeSet original,
            WorldBlockStateSource world,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            String operationIdPrefix
    ) throws IOException {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(history, "history");
        if (operationIdPrefix == null || operationIdPrefix.isBlank()) {
            throw new IllegalArgumentException("operationIdPrefix must be non-blank");
        }

        AppliedMutationCompaction compaction = AppliedMutationCompactor.compact(
                original,
                world,
                history,
                estimatedHistoryBytes,
                operationIdPrefix + "-applied"
        );
        return switch (compaction.state()) {
            case EMPTY -> PreparedRollback.empty();
            case CONFLICT -> PreparedRollback.conflict(
                    compaction.conflictX(), compaction.conflictY(), compaction.conflictZ());
            case COMPACTED -> reverseCompacted(compaction, history, estimatedHistoryBytes, operationIdPrefix);
        };
    }

    private static PreparedRollback reverseCompacted(
            AppliedMutationCompaction compaction,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            String operationIdPrefix
    ) throws IOException {
        long appliedChanges = compaction.appliedChanges();
        try (StoredChangeSet applied = compaction.compactedChangeSet()) {
            StoredChangeSet rollback = StoredChangeSetReverser.reverse(
                    applied,
                    history,
                    estimatedHistoryBytes,
                    operationIdPrefix + "-rollback"
            );
            return PreparedRollback.ready(appliedChanges, rollback);
        }
    }
}
