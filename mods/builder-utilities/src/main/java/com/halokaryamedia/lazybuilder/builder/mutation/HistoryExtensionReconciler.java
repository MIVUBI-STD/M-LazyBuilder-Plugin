package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** World-aware reconciliation for opaque extension frames through one mutation target. */
public final class HistoryExtensionReconciler {
    private HistoryExtensionReconciler() {}

    public static ExtensionReconciliationReport reconcile(
            StoredChangeSet stored,
            HistoryExtensionMutationTarget target
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(target, "target");

        long[] counts = new long[3];
        stored.visitExtensions(frame -> {
            byte[] actual = Objects.requireNonNull(target.read(frame), "extension actual payload");
            if (Arrays.equals(actual, frame.beforePayload())) counts[0]++;
            else if (Arrays.equals(actual, frame.afterPayload())) counts[1]++;
            else counts[2]++;
            return true;
        });

        long total = Math.addExact(Math.addExact(counts[0], counts[1]), counts[2]);
        ReconciliationState state;
        if (total == 0) state = ReconciliationState.EMPTY;
        else if (counts[2] > 0) state = ReconciliationState.CONFLICT;
        else if (counts[1] == total) state = ReconciliationState.FULLY_APPLIED;
        else if (counts[0] == total) state = ReconciliationState.NOT_APPLIED;
        else state = ReconciliationState.PARTIALLY_APPLIED;
        return new ExtensionReconciliationReport(total, counts[0], counts[1], counts[2], state);
    }
}
