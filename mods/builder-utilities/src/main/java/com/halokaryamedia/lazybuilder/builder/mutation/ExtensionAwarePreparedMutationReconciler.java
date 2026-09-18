package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/** Combines block and extension reconciliation into one recovery classification. */
public final class ExtensionAwarePreparedMutationReconciler {
    private ExtensionAwarePreparedMutationReconciler() {}

    public static ExtensionAwareReconciliationReport reconcile(
            StoredChangeSet stored,
            WorldBlockStateSource blocks,
            HistoryExtensionMutationTarget extensions
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        PreparedReconciliationReport blockReport =
                PreparedMutationReconciler.reconcile(stored, Objects.requireNonNull(blocks, "blocks"));
        ExtensionReconciliationReport extensionReport =
                HistoryExtensionReconciler.reconcile(stored, Objects.requireNonNull(extensions, "extensions"));

        ReconciliationState overall = combine(blockReport.state(), extensionReport.state());
        return new ExtensionAwareReconciliationReport(overall, blockReport, extensionReport);
    }

    private static ReconciliationState combine(
            ReconciliationState blocks,
            ExtensionReconciliationState extensions
    ) {
        if (blocks == ReconciliationState.CONFLICT
                || extensions == ExtensionReconciliationState.CONFLICT) {
            return ReconciliationState.CONFLICT;
        }
        boolean blockEmpty = blocks == ReconciliationState.EMPTY;
        boolean extEmpty = extensions == ExtensionReconciliationState.EMPTY;
        if (blockEmpty && extEmpty) return ReconciliationState.EMPTY;

        boolean allBefore = (blockEmpty || blocks == ReconciliationState.NOT_APPLIED)
                && (extEmpty || extensions == ExtensionReconciliationState.NOT_APPLIED);
        if (allBefore) return ReconciliationState.NOT_APPLIED;

        boolean allAfter = (blockEmpty || blocks == ReconciliationState.FULLY_APPLIED)
                && (extEmpty || extensions == ExtensionReconciliationState.FULLY_APPLIED);
        if (allAfter) return ReconciliationState.FULLY_APPLIED;

        return ReconciliationState.PARTIALLY_APPLIED;
    }
}
