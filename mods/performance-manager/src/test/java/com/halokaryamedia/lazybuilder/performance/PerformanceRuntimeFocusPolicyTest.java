package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure admission policy: never promote an optional work budget using stale
 * frame timing from a minimized, unfocused or just-refocused session.
 */
final class PerformanceRuntimeFocusPolicyTest {
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
