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

        long failedOps = Math.max(
                metrics.operationsFailed(),
                persisted.maxFailedOperations());
        if (failedOps > 0) {
            blockers.add("runtime evidence contains failed operations=" + failedOps);
        }

        long budgetFailures = Math.max(
                metrics.budgetExceeded(),
                persisted.maxBudgetExceeded());
        if (budgetFailures > 0) {
            blockers.add("runtime evidence contains dispatch budget exceeded="
                    + budgetFailures);
        }

        long extensionFailures = Math.max(
                metrics.extensionFailures(),
                persisted.maxExtensionFailures());
        if (extensionFailures > 0) {
            blockers.add("runtime evidence contains extension failures="
                    + extensionFailures);
        }

        long replayFailures = Math.max(
                metrics.historyReplayFailures(),
                persisted.maxHistoryReplayFailures());
        if (replayFailures > 0) {
            blockers.add("runtime evidence contains history replay failures="
                    + replayFailures);
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
