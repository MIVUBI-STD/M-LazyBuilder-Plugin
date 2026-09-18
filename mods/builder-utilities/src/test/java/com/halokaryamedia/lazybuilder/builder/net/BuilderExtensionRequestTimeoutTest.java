package com.halokaryamedia.lazybuilder.builder.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderExtensionRequestTimeoutTest {
    @Test
    void expiresAtSharedDeadline() {
        long started = 1_000L;
        assertFalse(BuilderExtensionRequestTimeout.expired(
                started,
                started + BuilderExtensionRequestTimeout.TIMEOUT_NANOS - 1L));
        assertTrue(BuilderExtensionRequestTimeout.expired(
                started,
                started + BuilderExtensionRequestTimeout.TIMEOUT_NANOS));
    }

    @Test
    void unsetStartDoesNotExpire() {
        assertFalse(BuilderExtensionRequestTimeout.expired(0L, Long.MAX_VALUE));
    }
}
