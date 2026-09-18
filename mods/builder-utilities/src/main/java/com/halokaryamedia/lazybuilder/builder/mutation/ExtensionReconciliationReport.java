package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionReconciliationReport(
        long totalExtensions,
        long beforeMatches,
        long afterMatches,
        long conflicts,
        ReconciliationState state
) {
    public ExtensionReconciliationReport {
        if (state == null) throw new NullPointerException("state");
        if (totalExtensions < 0 || beforeMatches < 0 || afterMatches < 0 || conflicts < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (beforeMatches + afterMatches + conflicts != totalExtensions) {
            throw new IllegalArgumentException("extension reconciliation counts must sum to total");
        }
    }
}
