package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoverableActiveOperationTest {
    @Test
    void contractCanRepresentDurableWorldExitPreservation() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        RecoverableActiveOperation operation = () -> called.set(true);
        operation.preserveForWorldExit();
        assertTrue(called.get());
    }
}
