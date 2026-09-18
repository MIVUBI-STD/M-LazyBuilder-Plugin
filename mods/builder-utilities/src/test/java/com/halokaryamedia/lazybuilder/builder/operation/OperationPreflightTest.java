package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationPreflightTest {
    @Test
    void guardsLimitsAndOverflowBeforeAllocation() {
        assertEquals(100, OperationPreflight.requireAtMost(100, 100, "blocks"));
        assertThrows(IllegalArgumentException.class, () ->
                OperationPreflight.requireAtMost(101, 100, "blocks"));
        assertEquals(9600, OperationPreflight.estimateBytes(100, 96, "history"));
        assertThrows(IllegalArgumentException.class, () ->
                OperationPreflight.multiply(Long.MAX_VALUE, 2, "overflow"));
    }
}
