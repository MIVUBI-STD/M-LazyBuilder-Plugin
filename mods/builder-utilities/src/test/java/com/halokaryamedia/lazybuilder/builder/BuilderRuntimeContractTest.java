package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuilderRuntimeContractTest {
    @Test
    void runtimeCloseContractIsAutoCloseableAndIdempotenceIsOwnedByRuntime() throws Exception {
        assertTrue(AutoCloseable.class.isAssignableFrom(BuilderRuntime.class));
        Method close = BuilderRuntime.class.getDeclaredMethod("close");
        assertTrue(java.lang.reflect.Modifier.isSynchronized(close.getModifiers()));
    }

    @Test
    void builderRejectsConcurrentMutationsFromDifferentAxiomTools() {
        assertDoesNotThrow(() -> BuilderRuntime.requireExclusiveAdmission(0));
        assertThrows(IllegalStateException.class,
                () -> BuilderRuntime.requireExclusiveAdmission(1));
        assertThrows(IllegalStateException.class,
                () -> BuilderRuntime.requireExclusiveAdmission(3));
    }
}
