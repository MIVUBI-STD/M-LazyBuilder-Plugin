package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptimizationProfitabilityWindowTest {
    @Test
    void lowValueWorkEntersFiniteCooldownThenRetries() {
        OptimizationProfitabilityWindow window =
                new OptimizationProfitabilityWindow(4, 3, 0.25D, 4L);

        for (int i = 0; i < 4; i++) window.record(false, 0L);

        assertFalse(window.allowAttempt());
        assertFalse(window.allowAttempt());
        assertFalse(window.allowAttempt());
        assertTrue(window.allowAttempt());
    }

    @Test
    void usefulWorkDoesNotEnterCooldown() {
        OptimizationProfitabilityWindow window =
                new OptimizationProfitabilityWindow(4, 3, 0.25D, 4L);

        for (int i = 0; i < 4; i++) window.record(true, 2L);

        assertTrue(window.allowAttempt());
        assertFalse(window.snapshot().coolingDown());
    }
}
