package com.halokaryamedia.lazybuilder.builder.operation;

/**
 * Declares the required terminal behavior after cooperative cancellation.
 */
public enum CancellationDisposition {
    KEEP_CHANGES,
    ROLLBACK
}
