package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionBudgetTest {
    @Test
    void acceptsStrictlyPositiveBounds() {
        ExecutionBudget budget = new ExecutionBudget(Duration.ofMillis(4), 2, 8192, 64L * 1024L * 1024L);
        assertEquals(Duration.ofMillis(4), budget.maxSliceDuration());
        assertEquals(2, budget.maxChunksInFlight());
    }

    @Test
    void rejectsUnboundedOrNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionBudget(Duration.ZERO, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionBudget(Duration.ofMillis(1), 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionBudget(Duration.ofMillis(1), 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionBudget(Duration.ofMillis(1), 1, 1, 0));
    }
}
