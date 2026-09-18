package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageTier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-based gate for retiring FAWE from the Builder workflow.
 *
 * <p>This evaluator does not score or predict. It lists concrete unmet gates:
 * authoritative payload coverage, durable journaling, clean recovery state, and
 * observed runtime proof.</p>
 */
public final class BuilderRetirementReadiness {
    private BuilderRetirementReadiness() {}

    public static Report evaluate(
            BuilderRuntime runtime,
            boolean biomeAuthority,
            boolean blockEntityAuthority,
            boolean entityAuthority,
            int recoverableCommitted,
            int incompleteJournals
    ) throws IOException {
        Objects.requireNonNull(runtime, "runtime");
        if (recoverableCommitted < 0 || incompleteJournals < 0) {
            throw new IllegalArgumentException("journal counts must be >= 0");
        }

        var metrics = runtime.metrics().snapshot();
        long proofSnapshots = runtime.proofStore().snapshotCount();
        List<String> blockers = new ArrayList<>();

        if (!runtime.history().hasTier(HistoryStorageTier.DISK)) {
            blockers.add("DISK durable mutation journal is unavailable");
        }
        if (!biomeAuthority) {
            blockers.add("BIOME authoritative server capability is unavailable");
        }
        if (!entityAuthority) {
            blockers.add("ENTITY authoritative server capability is unavailable");
        }
        if (!blockEntityAuthority) {
            blockers.add("BLOCK_ENTITY authoritative server capability is unavailable");
        }
        if (recoverableCommitted > 0) {
            blockers.add("unresolved committed recovery journals=" + recoverableCommitted);
        }
        if (incompleteJournals > 0) {
            blockers.add("incomplete recovery journals=" + incompleteJournals);
        }
        if (proofSnapshots == 0) {
            blockers.add("no persisted runtime proof snapshot exists");
        }
        if (metrics.operationsCompleted() == 0) {
            blockers.add("no completed Builder mutation observed in this runtime");
        }
        if (metrics.operationsFailed() > 0) {
            blockers.add("runtime has failed operations=" + metrics.operationsFailed());
        }
        if (metrics.budgetExceeded() > 0) {
            blockers.add("dispatch budget exceeded=" + metrics.budgetExceeded());
        }
        if (metrics.extensionFailures() > 0) {
            blockers.add("extension failures=" + metrics.extensionFailures());
        }

        return new Report(
                blockers.isEmpty() ? Status.READY_FOR_RETIREMENT_VALIDATION : Status.BLOCKED,
                List.copyOf(blockers),
                proofSnapshots,
                metrics.operationsCompleted()
        );
    }

    public enum Status {
        BLOCKED,
        READY_FOR_RETIREMENT_VALIDATION
    }

    public record Report(
            Status status,
            List<String> blockers,
            long proofSnapshots,
            long completedOperations
    ) {
        public Report {
            Objects.requireNonNull(status, "status");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if (proofSnapshots < 0 || completedOperations < 0) {
                throw new IllegalArgumentException("proof counts must be >= 0");
            }
        }
    }
}
