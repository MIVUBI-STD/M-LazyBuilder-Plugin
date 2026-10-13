package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoverableActiveOperationTest {
    @Test
    void contractCanRepresentDurableWorldExitPreservation() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        RecoverableActiveOperation operation = () -> called.set(true);
        operation.preserveForWorldExit();
        assertTrue(called.get());
    }

    @Test
    void tickHookIsOptionalForNonPumpingRecoveryOwners() {
        RecoverableActiveOperation operation = () -> { };
        operation.pump(); // preserves the functional recovery contract
    }

    @Test
    void activeOperationsExposeIndependentTickHook() {
        AtomicInteger ticks = new AtomicInteger();
        RecoverableActiveOperation operation = new RecoverableActiveOperation() {
            @Override public void preserveForWorldExit() { }
            @Override public void pump() { ticks.incrementAndGet(); }
        };
        operation.pump();
        operation.pump();
        assertEquals(2, ticks.get());
    }
}
