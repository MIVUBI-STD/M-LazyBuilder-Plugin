package com.halokaryamedia.lazybuilder.builder.operation;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owner-side cancellation controller. Executors receive only {@link #token()}.
 */
public final class CancellationSource {
    private final AtomicBoolean requested = new AtomicBoolean();
    private final CancellationToken token = requested::get;

    public CancellationToken token() {
        return token;
    }

    /**
     * @return true only for the first request that changed the cancellation state.
     */
    public boolean requestCancellation() {
        return requested.compareAndSet(false, true);
    }
}
