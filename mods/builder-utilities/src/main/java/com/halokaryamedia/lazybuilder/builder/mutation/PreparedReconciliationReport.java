package com.halokaryamedia.lazybuilder.builder.mutation;

/** Aggregated reconciliation result for one durable block-only mutation plan. */
public record PreparedReconciliationReport(
        long visitedChunks,
        long totalChanges,
        long beforeMatches,
        long afterMatches,
        long conflicts,
        ReconciliationState state
) {
    public PreparedReconciliationReport {
        if (visitedChunks < 0 || totalChanges < 0 || beforeMatches < 0 || afterMatches < 0 || conflicts < 0) {
            throw new IllegalArgumentException("reconciliation counts must be >= 0");
        }
        if (beforeMatches + afterMatches + conflicts != totalChanges) {
            throw new IllegalArgumentException("reconciliation counts must sum to totalChanges");
        }
        if (state == null) throw new NullPointerException("state");
    }

    public boolean requiresRecovery() {
        return state == ReconciliationState.PARTIALLY_APPLIED || state == ReconciliationState.CONFLICT;
    }
}
