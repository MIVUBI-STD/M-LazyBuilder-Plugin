package com.halokaryamedia.lazybuilder.builder;

/**
 * Cross-session runtime evidence. Capability evidence is aggregated only from clean
 * snapshots so an old failed development session cannot poison retirement proof forever.
 */
public record BuilderRuntimeProofEvidence(
        long snapshotCount,
        long cleanSnapshotCount,
        long rejectedSnapshotCount,
        long maxCompletedOperations,
        long maxCancelledOperations,
        long maxCompletedPlannedBlocks,
        long maxCompletedPlannedExtensions,
        long maxRollbackBlocks,
        long maxRollbackBlockEntityExtensions,
        long maxRollbackBiomeExtensions,
        long maxRollbackEntityExtensions,
        long maxForwardBlockEntityExtensions,
        long maxForwardBiomeExtensions,
        long maxForwardEntityExtensions
) {
    public BuilderRuntimeProofEvidence {
        if (snapshotCount < 0
                || cleanSnapshotCount < 0
                || rejectedSnapshotCount < 0
                || maxCompletedOperations < 0
                || maxCancelledOperations < 0
                || maxCompletedPlannedBlocks < 0
                || maxCompletedPlannedExtensions < 0
                || maxRollbackBlocks < 0
                || maxRollbackBlockEntityExtensions < 0
                || maxRollbackBiomeExtensions < 0
                || maxRollbackEntityExtensions < 0
                || maxForwardBlockEntityExtensions < 0
                || maxForwardBiomeExtensions < 0
                || maxForwardEntityExtensions < 0) {
            throw new IllegalArgumentException("proof evidence values must be >= 0");
        }
        if (cleanSnapshotCount + rejectedSnapshotCount != snapshotCount) {
            throw new IllegalArgumentException(
                    "clean + rejected snapshot counts must equal total");
        }
    }

    public static BuilderRuntimeProofEvidence empty() {
        return new BuilderRuntimeProofEvidence(
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0);
    }

    public long maxRollbackWork() {
        return Math.addExact(
                Math.addExact(
                        Math.addExact(maxRollbackBlocks, maxRollbackBlockEntityExtensions),
                        maxRollbackBiomeExtensions),
                maxRollbackEntityExtensions);
    }
}
