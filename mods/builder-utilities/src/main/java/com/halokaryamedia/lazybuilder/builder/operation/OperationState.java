package com.halokaryamedia.lazybuilder.builder.operation;

import java.util.EnumSet;
import java.util.Set;

public enum OperationState {
    CREATED,
    VALIDATING,
    PLANNING,
    QUEUED,
    RUNNING,
    CANCELLING,
    COMMITTING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<OperationState> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(OperationState next) {
        if (next == null || this == next || isTerminal()) {
            return false;
        }
        if (next == FAILED) {
            return true;
        }
        return switch (this) {
            case CREATED -> next == VALIDATING || next == CANCELLING;
            case VALIDATING -> next == PLANNING || next == CANCELLING;
            case PLANNING -> next == QUEUED || next == CANCELLING;
            case QUEUED -> next == RUNNING || next == CANCELLING;
            case RUNNING -> next == COMMITTING || next == CANCELLING;
            case CANCELLING -> next == CANCELLED || next == COMMITTING;
            case COMMITTING -> next == COMPLETED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
