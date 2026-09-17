package com.halokaryamedia.lazybuilder.builder.operation;

import java.util.Objects;

/**
 * Immutable lifecycle/progress snapshot shared by every future Builder operation.
 */
public record OperationLifecycle(
        OperationState state,
        long processedWork,
        long totalWork,
        String failureMessage
) {
    public OperationLifecycle {
        Objects.requireNonNull(state, "state");
        if (processedWork < 0) {
            throw new IllegalArgumentException("processedWork must be >= 0");
        }
        if (totalWork < 0) {
            throw new IllegalArgumentException("totalWork must be >= 0");
        }
        if (processedWork > totalWork) {
            throw new IllegalArgumentException("processedWork cannot exceed totalWork");
        }
        if (state == OperationState.FAILED) {
            if (failureMessage == null || failureMessage.isBlank()) {
                throw new IllegalArgumentException("FAILED lifecycle requires a failure message");
            }
        } else if (failureMessage != null) {
            throw new IllegalArgumentException("failureMessage is valid only for FAILED lifecycle");
        }
    }

    public static OperationLifecycle created(long totalWork) {
        return new OperationLifecycle(OperationState.CREATED, 0, totalWork, null);
    }

    public OperationLifecycle transitionTo(OperationState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal operation transition: " + state + " -> " + next);
        }
        return new OperationLifecycle(next, processedWork, totalWork, null);
    }

    public OperationLifecycle advance(long completedWork) {
        if (state != OperationState.RUNNING) {
            throw new IllegalStateException("Operation progress can advance only while RUNNING");
        }
        if (completedWork <= 0) {
            throw new IllegalArgumentException("completedWork must be > 0");
        }
        long next = Math.addExact(processedWork, completedWork);
        if (next > totalWork) {
            throw new IllegalArgumentException("Operation progress cannot exceed totalWork");
        }
        return new OperationLifecycle(state, next, totalWork, null);
    }

    public OperationLifecycle fail(String message) {
        if (state.isTerminal()) {
            throw new IllegalStateException("Terminal operation cannot fail again: " + state);
        }
        return new OperationLifecycle(OperationState.FAILED, processedWork, totalWork, message);
    }

    public double progressFraction() {
        if (totalWork == 0) {
            return state == OperationState.COMPLETED ? 1.0 : 0.0;
        }
        return (double) processedWork / (double) totalWork;
    }
}
