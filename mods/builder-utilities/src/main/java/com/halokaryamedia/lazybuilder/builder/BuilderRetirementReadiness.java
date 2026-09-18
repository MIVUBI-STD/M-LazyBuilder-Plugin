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
    public static final long MIN_LARGE_EDIT_PROOF_BLOCKS = 1_000_000L;

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
        BuilderRuntimeProofEvidence persisted =
                runtime.proofStore().aggregateEvidence();
        long proofSnapshots = persisted.snapshotCount();
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
        } else if (persisted.cleanSnapshotCount() == 0) {
            blockers.add("persisted proof snapshots exist but none are clean");
        }
        long completedOps = Math.max(
                metrics.operationsCompleted(),
                persisted.maxCompletedOperations());
        if (completedOps == 0) {
            blockers.add("no completed Builder mutation observed in runtime evidence");
        }

        long maxCompletedBlocks = Math.max(
                metrics.maxCompletedPlannedBlocks(),
                persisted.maxCompletedPlannedBlocks());
        if (maxCompletedBlocks < MIN_LARGE_EDIT_PROOF_BLOCKS) {
            blockers.add("no completed large-edit proof >= "
                    + MIN_LARGE_EDIT_PROOF_BLOCKS + " blocks");
        }

        long currentRollbackWork = metrics.rollbackBlocksDispatched()
                + metrics.rollbackBlockEntityExtensions()
                + metrics.rollbackBiomeExtensions()
                + metrics.rollbackEntityExtensions();
        long rollbackWork = Math.max(
                currentRollbackWork,
                persisted.maxRollbackWork());
        long cancelledOps = Math.max(
                metrics.operationsCancelled(),
                persisted.maxCancelledOperations());
        if (cancelledOps == 0 || rollbackWork == 0) {
            blockers.add("no observed cancel/rollback proof that reverted applied work");
        }

        long blockEntityProof = Math.max(
                metrics.forwardBlockEntityExtensions(),
                persisted.maxForwardBlockEntityExtensions());
        if (blockEntityAuthority && blockEntityProof == 0) {
            blockers.add("BLOCK_ENTITY authority exists but has no observed runtime apply proof");
        }

        long biomeProof = Math.max(
                metrics.forwardBiomeExtensions(),
                persisted.maxForwardBiomeExtensions());
        if (biomeAuthority && biomeProof == 0) {
            blockers.add("BIOME authority exists but has no observed runtime apply proof");
        }

        long entityProof = Math.max(
                metrics.forwardEntityExtensions(),
                persisted.maxForwardEntityExtensions());
        if (entityAuthority && entityProof == 0) {
            blockers.add("ENTITY authority exists but has no observed runtime apply proof");
        }

        if (metrics.operationsFailed() > 0) {
            blockers.add("current runtime contains failed operations="
                    + metrics.operationsFailed());
        }
        if (metrics.budgetExceeded() > 0) {
            blockers.add("current runtime contains dispatch budget exceeded="
                    + metrics.budgetExceeded());
        }
        if (metrics.extensionFailures() > 0) {
            blockers.add("current runtime contains extension failures="
                    + metrics.extensionFailures());
        }
        if (metrics.historyReplayFailures() > 0) {
            blockers.add("current runtime contains history replay failures="
                    + metrics.historyReplayFailures());
        }

        return new Report(
                blockers.isEmpty() ? Status.READY_FOR_RETIREMENT_VALIDATION : Status.BLOCKED,
                List.copyOf(blockers),
                proofSnapshots,
                Math.max(metrics.operationsCompleted(), persisted.maxCompletedOperations())
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
