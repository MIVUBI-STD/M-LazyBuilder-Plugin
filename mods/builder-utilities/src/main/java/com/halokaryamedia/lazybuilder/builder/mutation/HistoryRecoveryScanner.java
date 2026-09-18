package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Reopens durable orphan History files and classifies actual world state.
 * It never mutates the world; resume/rollback/discard remains an explicit caller decision.
 */
public final class HistoryRecoveryScanner {
    private HistoryRecoveryScanner() {}

    public static List<RecoveredMutationCandidate> scan(
            DiskChangeSetStorage storage,
            WorldBlockStateSource world,
            HistoryExtensionTargetRegistry extensions
    ) throws IOException {
        return scan(storage, world, extensions, operationId -> true);
    }

    public static List<RecoveredMutationCandidate> scan(
            DiskChangeSetStorage storage,
            WorldBlockStateSource world,
            HistoryExtensionTargetRegistry extensions,
            Predicate<String> operationFilter
    ) throws IOException {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(operationFilter, "operationFilter");
        storage.promoteRecoverableIncomplete();
        return classifyRecovered(
                storage.recoverCommitted(operationFilter),
                world,
                extensions,
                false
        );
    }

    public static List<RecoveredMutationCandidate> scanBlocksOnly(
            DiskChangeSetStorage storage,
            WorldBlockStateSource world
    ) throws IOException {
        return scanBlocksOnly(storage, world, operationId -> true);
    }

    public static List<RecoveredMutationCandidate> scanBlocksOnly(
            DiskChangeSetStorage storage,
            WorldBlockStateSource world,
            Predicate<String> operationFilter
    ) throws IOException {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operationFilter, "operationFilter");
        storage.promoteRecoverableIncomplete();
        return classifyRecovered(
                storage.recoverCommitted(operationFilter),
                world,
                HistoryExtensionTargetRegistry.empty(),
                true
        );
    }

    private static List<RecoveredMutationCandidate> classifyRecovered(
            List<StoredChangeSet> recovered,
            WorldBlockStateSource world,
            HistoryExtensionTargetRegistry extensions,
            boolean requireNoExtensions
    ) throws IOException {
        List<RecoveredMutationCandidate> result = new ArrayList<>(recovered.size());

        try {
            for (StoredChangeSet set : recovered) {
                if (requireNoExtensions && set.extensionCount() != 0) {
                    throw new IllegalArgumentException(
                            "Recovered history contains extension frames; an extension registry is required");
                }

                PreparedReconciliationReport blockReport =
                        PreparedMutationReconciler.reconcile(set, world);
                ExtensionReconciliationReport extensionReport =
                        set.extensionCount() == 0
                                ? new ExtensionReconciliationReport(
                                        0, 0, 0, 0, ReconciliationState.EMPTY)
                                : PreparedExtensionMutationReconciler.reconcile(set, extensions);

                result.add(new RecoveredMutationCandidate(
                        set,
                        blockReport,
                        extensionReport,
                        combine(blockReport.state(), extensionReport.state())
                ));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException failure) {
            closeRecovered(recovered, failure);
            throw failure;
        }
    }

    private static void closeRecovered(List<StoredChangeSet> recovered, Throwable failure) {
        for (StoredChangeSet set : recovered) {
            try {
                set.close();
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
        }
    }

    public static ReconciliationState combine(ReconciliationState blocks, ReconciliationState extensions) {
        if (blocks == ReconciliationState.CONFLICT || extensions == ReconciliationState.CONFLICT) {
            return ReconciliationState.CONFLICT;
        }
        if (blocks == ReconciliationState.PARTIALLY_APPLIED
                || extensions == ReconciliationState.PARTIALLY_APPLIED) {
            return ReconciliationState.PARTIALLY_APPLIED;
        }
        if (blocks == ReconciliationState.EMPTY) return extensions;
        if (extensions == ReconciliationState.EMPTY) return blocks;
        if (blocks == extensions) return blocks;
        return ReconciliationState.PARTIALLY_APPLIED;
    }
}
