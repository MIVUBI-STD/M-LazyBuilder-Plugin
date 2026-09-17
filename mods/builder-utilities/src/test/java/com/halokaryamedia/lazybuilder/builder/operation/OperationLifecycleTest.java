package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationLifecycleTest {
    @Test
    void tracksProgressWithoutAllowingOverflow() {
        OperationLifecycle lifecycle = OperationLifecycle.created(100)
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED)
                .transitionTo(OperationState.RUNNING)
                .advance(40)
                .advance(60);

        assertEquals(100, lifecycle.processedWork());
        assertEquals(1.0, lifecycle.progressFraction());
        assertThrows(IllegalArgumentException.class, () -> lifecycle.advance(1));
    }

    @Test
    void rejectsProgressOutsideRunningState() {
        OperationLifecycle lifecycle = OperationLifecycle.created(10);
        assertThrows(IllegalStateException.class, () -> lifecycle.advance(1));
    }

    @Test
    void failureRequiresMessageAndPreservesProgress() {
        OperationLifecycle running = OperationLifecycle.created(10)
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED)
                .transitionTo(OperationState.RUNNING)
                .advance(4);

        OperationLifecycle failed = running.fail("chunk write rejected");
        assertEquals(OperationState.FAILED, failed.state());
        assertEquals(4, failed.processedWork());
        assertEquals("chunk write rejected", failed.failureMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new OperationLifecycle(OperationState.FAILED, 0, 0, " "));
    }
}
