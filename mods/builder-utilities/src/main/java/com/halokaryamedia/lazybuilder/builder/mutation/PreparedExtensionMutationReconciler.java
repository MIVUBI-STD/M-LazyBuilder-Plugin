package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** World-aware reconciliation for opaque History extension frames. */
public final class PreparedExtensionMutationReconciler {
    private PreparedExtensionMutationReconciler() {}

    public static ExtensionReconciliationReport reconcile(
            StoredChangeSet prepared,
            HistoryExtensionTargetRegistry registry
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(registry, "registry");

        long[] counts = new long[3]; // before, after, conflicts
        prepared.visitExtensions(frame -> {
            HistoryExtensionMutationTarget target = registry.require(frame.typeId());
            byte[] actual = Objects.requireNonNull(target.read(frame), "extension target read payload");
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
