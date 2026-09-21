package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StageTimingMetricsTest {
    @BeforeEach
    void reset() {
        StageTimingMetrics.resetForTest();
    }

    @Test
    void staysSilentWhenDisabled() {
        StageTimingMetrics.setEnabledForTest(false);
        StageTimingMetrics.record(StageTimingMetrics.Stage.ENTITY_CULLING, 5_000_000L);

        assertEquals(0L, StageTimingMetrics.snapshot(
                StageTimingMetrics.Stage.ENTITY_CULLING
        ).samples());
    }

    @Test
    void reportsAverageAndMaximumWithoutKeepingHistory() {
        StageTimingMetrics.record(StageTimingMetrics.Stage.ENTITY_CULLING, 1_000_000L);
        StageTimingMetrics.record(StageTimingMetrics.Stage.ENTITY_CULLING, 3_000_000L);

        StageTimingMetrics.Snapshot snapshot =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.ENTITY_CULLING);

        assertEquals(2L, snapshot.samples());
        assertEquals(2.0D, snapshot.averageMs(), 0.0001D);
        assertEquals(3.0D, snapshot.maxMs(), 0.0001D);
    }
}
