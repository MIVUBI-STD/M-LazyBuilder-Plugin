package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure admission policy: never promote an optional work budget using stale
 * frame timing from a minimized, unfocused or just-refocused session.
 */
final class PerformanceRuntimeFocusPolicyTest {
    @Test
    void disablingRenderingReleasesPreviouslyDeferredTasksOverSeveralTicks() {
        // 128 queued tasks must never be submitted in a single options click.
        assertEquals(16, PerformanceRuntime.deferredDrainBudget(
                false, new PerformanceGovernor.Profile(
                        PerformanceGovernor.Mode.PROTECTIVE, 12, 2, 10, 4),
                FramePressure.HEAVY
        ));
        assertEquals(1, PerformanceRuntime.deferredDrainBudget(
                true, new PerformanceGovernor.Profile(
                        PerformanceGovernor.Mode.PROTECTIVE, 12, 2, 10, 4),
                FramePressure.HEAVY
        ));
        assertEquals(16, PerformanceRuntime.deferredDrainBudget(
                true, PerformanceGovernor.Profile.balanced(), FramePressure.NORMAL
        ));
    }

    @Test
    void requiresFocusedWorldAndFreshFrameSamples() {
        assertFalse(PerformanceRuntime.shouldUseFramePressure(false, 120));
        assertFalse(PerformanceRuntime.shouldUseFramePressure(true, 0));
        assertFalse(PerformanceRuntime.shouldUseFramePressure(true, 1));
        assertFalse(PerformanceRuntime.shouldUseFramePressure(true, 2));
        assertTrue(PerformanceRuntime.shouldUseFramePressure(true, 3));
        assertTrue(PerformanceRuntime.shouldUseFramePressure(true, 60));
    }
}
