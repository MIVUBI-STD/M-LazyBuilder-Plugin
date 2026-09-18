package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** World-aware reconciliation for opaque extension frames. */
public final class HistoryExtensionReconciler {
    private HistoryExtensionReconciler() {}

    public static ExtensionReconciliationReport reconcile(
            StoredChangeSet stored,
            HistoryExtensionMutationTarget target
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(target, "target");

        Mutable counts = new Mutable();
        stored.visitExtensions(frame -> {
            classify(frame, target, counts);
            return true;
        });

        ExtensionReconciliationState state;
        if (counts.conflicts > 0) state = ExtensionReconciliationState.CONFLICT;
        else if (counts.after > 0 && counts.before == 0) state = ExtensionReconciliationState.FULLY_APPLIED;
        else if (counts.before > 0 && counts.after == 0) state = ExtensionReconciliationState.NOT_APPLIED;
        else state = ExtensionReconciliationState.PARTIALLY_APPLIED;

        return new ExtensionReconciliationReport(state, counts.before, counts.after, counts.conflicts);
    }

    private static void classify(
            HistoryExtensionFrame frame,
            HistoryExtensionMutationTarget target,
            Mutable counts
    ) throws IOException {
        byte[] actual = Objects.requireNonNull(
                target.read(frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey()),
                "extension actual payload");
        if (Arrays.equals(actual, frame.afterPayload())) {
            counts.after++;
        } else if (Arrays.equals(actual, frame.beforePayload())) {
            counts.before++;
        } else {
            counts.conflicts++;
        }
    }

    private static final class Mutable {
        long before;
        long after;
        long conflicts;
    }
}
