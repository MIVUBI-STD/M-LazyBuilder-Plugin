package com.halokaryamedia.lazybuilder.builder.operation;

/**
 * Defines whether an operation may observe its own writes while it executes.
 */
public enum MutationReadMode {
    /** Suitable only when traversal order cannot change the intended result. */
    IN_PLACE,
    /** Reads are resolved from a stable pre-operation view. */
    SNAPSHOT_READ
}
