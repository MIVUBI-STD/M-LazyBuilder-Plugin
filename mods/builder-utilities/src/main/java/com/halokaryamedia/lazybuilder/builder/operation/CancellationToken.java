package com.halokaryamedia.lazybuilder.builder.operation;

import java.util.concurrent.CancellationException;

/**
 * Read-only cooperative cancellation view shared with operation executors.
 */
@FunctionalInterface
public interface CancellationToken {
    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException("Builder operation cancellation requested");
        }
    }
}
