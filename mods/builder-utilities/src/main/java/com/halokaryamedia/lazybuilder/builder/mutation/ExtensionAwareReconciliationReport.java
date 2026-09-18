package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionAwareReconciliationReport(
        ReconciliationState state,
        PreparedReconciliationReport blocks,
        ExtensionReconciliationReport extensions
) {
    public ExtensionAwareReconciliationReport {
        if (state == null || blocks == null || extensions == null) {
            throw new NullPointerException("reconciliation values");
        }
    }
}
