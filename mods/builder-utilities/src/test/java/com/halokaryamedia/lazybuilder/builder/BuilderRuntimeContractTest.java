package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderRuntimeContractTest {
    @Test
    void runtimeCloseContractIsAutoCloseableAndIdempotenceIsOwnedByRuntime() throws Exception {
        assertTrue(AutoCloseable.class.isAssignableFrom(BuilderRuntime.class));
        Method close = BuilderRuntime.class.getDeclaredMethod("close");
        assertTrue(java.lang.reflect.Modifier.isSynchronized(close.getModifiers()));
    }
}
