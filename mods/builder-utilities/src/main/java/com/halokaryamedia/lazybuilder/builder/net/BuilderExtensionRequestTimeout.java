package com.halokaryamedia.lazybuilder.builder.net;

import java.time.Duration;

/** Shared bounded wait policy for authoritative Builder extension requests. */
public final class BuilderExtensionRequestTimeout {
    public static final long TIMEOUT_NANOS = Duration.ofSeconds(15).toNanos();

    private BuilderExtensionRequestTimeout() {}

    public static boolean expired(long startedNanos, long nowNanos) {
        if (startedNanos <= 0L) return false;
        return nowNanos - startedNanos >= TIMEOUT_NANOS;
    }
}
