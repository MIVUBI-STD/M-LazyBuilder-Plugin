package com.halokaryamedia.lazybuilder.performance.rendering;

/**
 * Small session-scoped failure circuit breaker.
 *
 * A bounded number of failures is tolerated for transient faults. Once the threshold is reached the
 * guarded optimization stays disabled until its owning session is reset.
 */
public final class RuntimeCircuitBreaker {
    private final int failureThreshold;
    private int failures;
    private boolean open;

    public RuntimeCircuitBreaker(int failureThreshold) {
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be positive");
        this.failureThreshold = failureThreshold;
    }

    public boolean allow() {
        return !open;
    }

    public boolean recordFailure() {
        if (open) return true;
        failures++;
        if (failures >= failureThreshold) open = true;
        return open;
    }

    public void reset() {
        failures = 0;
        open = false;
    }

    public int failures() {
        return failures;
    }

    public boolean open() {
        return open;
    }
}
