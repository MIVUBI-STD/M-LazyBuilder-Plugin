package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoryJournalSummaryTest {
    @Test
    void blockOnlyReflectsExtensionCount() {
        assertTrue(new HistoryJournalSummary("a", 10, 0).blockOnly());
        assertFalse(new HistoryJournalSummary("b", 10, 1).blockOnly());
    }
}
