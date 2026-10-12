package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformanceGovernorTest {
    @Test
    void entersProtectiveModeAfterSustainedPressure() {
        PerformanceGovernor governor = new PerformanceGovernor();
        PerformanceGovernor.Input input = new PerformanceGovernor.Input(
                FramePressure.HEAVY,
                40.0D,
                80,
                32,
                0,
                0.92D,
                100,
                2,
                0.7D
        );

        governor.update(input);
        governor.update(input);
        PerformanceGovernor.Profile profile = governor.update(input);

        assertEquals(PerformanceGovernor.Mode.PROTECTIVE, profile.mode());
        assertTrue(profile.chunkUploadBudget() <= 12);
    }

    @Test
    void nonFiniteTelemetryNeverEntersProtectiveModeByAccident() {
        PerformanceGovernor governor = new PerformanceGovernor();
        PerformanceGovernor.Input invalid = new PerformanceGovernor.Input(
                FramePressure.NORMAL,
                Double.NaN,
                0,
                0,
                8,
                Double.POSITIVE_INFINITY,
                0L,
                0L,
                Double.NaN
        );
        for (int i = 0; i < 180; i++) governor.update(invalid);
        assertEquals(PerformanceGovernor.Mode.BALANCED, governor.profile().mode());
        assertEquals(0.0D, invalid.frameControlMs());
        assertEquals(0.0D, invalid.usedMemoryRatio());
        assertEquals(0.0D, invalid.cullingCpuAverageMs());
    }

    @Test
    void longSustainedPressureKeepsStableProtectiveDecision() {
        PerformanceGovernor governor = new PerformanceGovernor();
        PerformanceGovernor.Input heavy = new PerformanceGovernor.Input(
                FramePressure.HEAVY, 80.0D, 96, 60, 0, 0.95D, 500, 1, 0.9D
        );
        for (int i = 0; i < 100_000; i++) governor.update(heavy);
        assertEquals(PerformanceGovernor.Mode.PROTECTIVE, governor.profile().mode());
        assertTrue(governor.profile().chunkUploadBudget() <= 12);
    }

    @Test
    void poorCullingProfitabilityReducesBudget() {
        PerformanceGovernor governor = new PerformanceGovernor();
        PerformanceGovernor.Profile profile = governor.update(new PerformanceGovernor.Input(
                FramePressure.NORMAL,
                16.0D,
                0,
                0,
                8,
                0.5D,
                1000,
                10,
                0.8D
        ));

        assertEquals(25, profile.cullingBudgetPercent());
    }

    @Test
    void stableWorkloadEventuallyUsesThroughputMode() {
        PerformanceGovernor governor = new PerformanceGovernor();
        PerformanceGovernor.Input input = new PerformanceGovernor.Input(
                FramePressure.NORMAL,
                12.0D,
                2,
                2,
                8,
                0.4D,
                100,
                60,
                0.05D
        );

        PerformanceGovernor.Profile profile = null;
        for (int i = 0; i < 120; i++) profile = governor.update(input);

        assertEquals(PerformanceGovernor.Mode.THROUGHPUT, profile.mode());
    }
}
