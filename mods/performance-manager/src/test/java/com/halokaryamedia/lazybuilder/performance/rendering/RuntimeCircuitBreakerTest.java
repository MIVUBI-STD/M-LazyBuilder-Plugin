package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RuntimeCircuitBreakerTest {
    @Test
    void opensAfterConfiguredFailureThreshold() {
        RuntimeCircuitBreaker breaker = new RuntimeCircuitBreaker(3);

        assertTrue(breaker.allow());
        assertFalse(breaker.recordFailure());
        assertFalse(breaker.recordFailure());
        assertTrue(breaker.recordFailure());

        assertTrue(breaker.open());
        assertFalse(breaker.allow());
        assertEquals(3, breaker.failures());
    }

    @Test
    void resetRestoresSessionScopedOptimization() {
        RuntimeCircuitBreaker breaker = new RuntimeCircuitBreaker(2);
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.open());

        breaker.reset();

        assertTrue(breaker.allow());
        assertFalse(breaker.open());
        assertEquals(0, breaker.failures());
    }
}
