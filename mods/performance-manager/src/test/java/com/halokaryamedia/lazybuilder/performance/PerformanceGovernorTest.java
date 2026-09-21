package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(profile.optionalGpuWorkAllowed());
        assertTrue(profile.chunkUploadBudget() <= 12);
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
        assertTrue(profile.optionalGpuWorkAllowed());
    }
}
