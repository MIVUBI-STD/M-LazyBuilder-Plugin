package com.halokaryamedia.lazybuilder.builder.history;

public record HistoryJournalSummary(
        String operationId,
        long changeCount,
        long extensionCount
) {
    public HistoryJournalSummary {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        if (changeCount < 0 || extensionCount < 0) {
            throw new IllegalArgumentException("journal counts must be >= 0");
        }
    }

    public boolean blockOnly() {
        return extensionCount == 0;
    }
}
