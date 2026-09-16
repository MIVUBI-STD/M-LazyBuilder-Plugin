package com.halokaryamedia.lazybuilder.utility.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactDebugMetricsFormattingTest {
    @Test
    void invalidCpuIsUnavailableAndValidCpuIsClamped() {
        assertEquals("Unavailable", CompactDebugMetrics.formatCpuPercent(Double.NaN));
        assertEquals("100%", CompactDebugMetrics.formatCpuPercent(125.0D));
        assertEquals("0%", CompactDebugMetrics.formatCpuPercent(-25.0D));
    }

    @Test
    void impossibleMemorySnapshotsAreUnavailable() {
        assertEquals("Unavailable", CompactDebugMetrics.formatMemory(-1L, 8_000L));
        assertEquals("Unavailable", CompactDebugMetrics.formatMemory(1_000L, 0L));
        assertEquals("Unavailable", CompactDebugMetrics.formatMemory(9_000L, 8_000L));
    }
}
