package com.halokaryamedia.lazybuilder.builder.mutation;

/**
 * Observed relationship between a planned chunk mutation and current world state.
 */
public enum ReconciliationState {
    EMPTY,
    NOT_APPLIED,
    FULLY_APPLIED,
    PARTIALLY_APPLIED,
    CONFLICT
}
