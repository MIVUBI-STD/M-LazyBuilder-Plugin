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

        ReconciliationState overall = HistoryRecoveryScanner.combine(
                blockReport.state(), extensionReport.state());
        return new ExtensionAwareReconciliationReport(overall, blockReport, extensionReport);
    }
}
