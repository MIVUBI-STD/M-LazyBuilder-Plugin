package com.halokaryamedia.lazybuilder.builder;

/**
 * Cross-session runtime evidence aggregated with max semantics so repeated snapshots
 * from one session never double-count work.
 */
public record BuilderRuntimeProofEvidence(
        long snapshotCount,
        long maxCompletedOperations,
        long maxCancelledOperations,
        long maxFailedOperations,
        long maxCompletedPlannedBlocks,
        long maxCompletedPlannedExtensions,
        long maxRollbackBlocks,
        long maxRollbackBiomeExtensions,
        long maxRollbackEntityExtensions,
        long maxForwardBiomeExtensions,
        long maxForwardEntityExtensions,
        long maxBudgetExceeded,
        long maxExtensionFailures,
        long maxHistoryReplayFailures
) {
    public BuilderRuntimeProofEvidence {
        if (snapshotCount < 0
                || maxCompletedOperations < 0
                || maxCancelledOperations < 0
                || maxFailedOperations < 0
                || maxCompletedPlannedBlocks < 0
                || maxCompletedPlannedExtensions < 0
                || maxRollbackBlocks < 0
                || maxRollbackBiomeExtensions < 0
                || maxRollbackEntityExtensions < 0
                || maxForwardBiomeExtensions < 0
                || maxForwardEntityExtensions < 0
                || maxBudgetExceeded < 0
                || maxExtensionFailures < 0
                || maxHistoryReplayFailures < 0) {
            throw new IllegalArgumentException("proof evidence values must be >= 0");
        }
    }

    public static BuilderRuntimeProofEvidence empty() {
        return new BuilderRuntimeProofEvidence(
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    public long maxRollbackWork() {
        return Math.addExact(
                Math.addExact(maxRollbackBlocks, maxRollbackBiomeExtensions),
                maxRollbackEntityExtensions);
    }
}
