package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistorySizingPolicyTest {
    private final HistorySizingPolicy policy = new HistorySizingPolicy(1_000, 10_000);

    @Test
    void selectsTierFromEstimatedHistorySize() {
        assertEquals(HistoryStorageTier.MEMORY,
                policy.select(HistoryRequirement.REQUIRED, 1_000).orElseThrow());
        assertEquals(HistoryStorageTier.COMPRESSED_MEMORY,
                policy.select(HistoryRequirement.REQUIRED, 1_001).orElseThrow());
        assertEquals(HistoryStorageTier.DISK,
                policy.select(HistoryRequirement.REQUIRED, 10_001).orElseThrow());
    }

    @Test
    void noHistoryRequirementProducesNoStorageTier() {
        assertTrue(policy.select(HistoryRequirement.NONE, Long.MAX_VALUE).isEmpty());
    }

    @Test
    void rejectsInvalidThresholdsAndNegativeEstimates() {
        assertThrows(IllegalArgumentException.class, () -> new HistorySizingPolicy(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new HistorySizingPolicy(100, 99));
        assertThrows(IllegalArgumentException.class,
                () -> policy.select(HistoryRequirement.REQUIRED, -1));
    }
}
