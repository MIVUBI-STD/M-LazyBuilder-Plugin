package com.halokaryamedia.lazybuilder.builder.mutation;

public record ChunkReconciliationReport(
        int chunkX,
        int chunkZ,
        long totalChanges,
        long beforeMatches,
        long afterMatches,
        long conflicts,
        ReconciliationState state
) {
    public ChunkReconciliationReport {
        if (totalChanges < 0 || beforeMatches < 0 || afterMatches < 0 || conflicts < 0) {
            throw new IllegalArgumentException("reconciliation counts must be >= 0");
        }
        if (beforeMatches + afterMatches + conflicts != totalChanges) {
            throw new IllegalArgumentException("reconciliation counts must sum to totalChanges");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
    }

    public boolean hasConflict() {
        return conflicts > 0;
    }
}
