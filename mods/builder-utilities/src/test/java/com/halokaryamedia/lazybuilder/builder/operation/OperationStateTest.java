package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationStateTest {
    @Test
    void acceptsCanonicalSuccessPath() {
        OperationState current = OperationState.CREATED;
        OperationState[] path = {
                OperationState.VALIDATING,
                OperationState.PLANNING,
                OperationState.QUEUED,
                OperationState.RUNNING,
                OperationState.COMMITTING,
                OperationState.COMPLETED
        };

        for (OperationState next : path) {
            assertTrue(current.canTransitionTo(next), current + " should transition to " + next);
            current = next;
        }
        assertTrue(current.isTerminal());
    }

    @Test
    void cancellationHasExplicitTerminalPath() {
        assertTrue(OperationState.RUNNING.canTransitionTo(OperationState.CANCELLING));
        assertTrue(OperationState.CANCELLING.canTransitionTo(OperationState.CANCELLED));
        assertTrue(OperationState.CANCELLED.isTerminal());
        assertFalse(OperationState.CANCELLED.canTransitionTo(OperationState.RUNNING));
    }

    @Test
    void anyNonTerminalStateMayFail() {
        for (OperationState state : OperationState.values()) {
            if (!state.isTerminal()) {
                assertTrue(state.canTransitionTo(OperationState.FAILED));
            }
        }
    }
}
