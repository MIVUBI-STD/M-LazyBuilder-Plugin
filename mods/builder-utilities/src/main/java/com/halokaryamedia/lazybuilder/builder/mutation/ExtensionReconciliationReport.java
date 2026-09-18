package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionReconciliationReport(
        ExtensionReconciliationState state,
        long beforeMatches,
        long afterMatches,
        long conflicts
) {
    public ExtensionReconciliationReport {
        if (state == null) throw new NullPointerException("state");
        if (beforeMatches < 0 || afterMatches < 0 || conflicts < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
    }
}
