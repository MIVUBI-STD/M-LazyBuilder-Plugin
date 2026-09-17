package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class CancellationSourceTest {
    @Test
    void cancellationIsIdempotentAndVisibleThroughReadOnlyToken() {
        CancellationSource source = new CancellationSource();
        CancellationToken token = source.token();

        assertFalse(token.isCancellationRequested());
        assertTrue(source.requestCancellation());
        assertFalse(source.requestCancellation());
        assertTrue(token.isCancellationRequested());
        assertThrows(CancellationException.class, token::throwIfCancellationRequested);
    }
}
