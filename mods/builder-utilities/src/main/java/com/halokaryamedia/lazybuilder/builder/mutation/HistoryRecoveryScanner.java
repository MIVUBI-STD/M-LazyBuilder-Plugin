package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(extensions, "extensions");

        List<StoredChangeSet> recovered = storage.recoverCommitted();
        List<RecoveredMutationCandidate> result = new ArrayList<>(recovered.size());

        try {
            for (StoredChangeSet set : recovered) {
                PreparedReconciliationReport blockReport =
                        PreparedMutationReconciler.reconcile(set, world);
                ExtensionReconciliationReport extensionReport =
                        PreparedExtensionMutationReconciler.reconcile(set, extensions);
                result.add(new RecoveredMutationCandidate(
                        set,
                        blockReport,
                        extensionReport,
                        combine(blockReport.state(), extensionReport.state())
                ));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException failure) {
            for (RecoveredMutationCandidate item : result) {
                try { item.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            }
            for (int i = result.size(); i < recovered.size(); i++) {
                try { recovered.get(i).close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            }
            throw failure;
        }
    }

    public static List<RecoveredMutationCandidate> scanBlocksOnly(
            DiskChangeSetStorage storage,
            WorldBlockStateSource world
    ) throws IOException {
        for (StoredChangeSet set : storage.recoverCommitted()) {
            // Close the probe immediately; scan() will reopen canonical owners.
            boolean extensions = set.extensionCount() != 0;
            set.close();
            if (extensions) {
                throw new IllegalArgumentException(
                        "Recovered history contains extension frames; an extension registry is required");
            }
        }
        return scan(storage, world, HistoryExtensionTargetRegistry.empty());
    }

    static ReconciliationState combine(ReconciliationState blocks, ReconciliationState extensions) {
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
