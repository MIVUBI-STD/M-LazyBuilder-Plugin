package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure proof admission; live logger session boundaries require Fabric acceptance. */
final class PerformanceProofSessionGateTest {
    @Test
    void dropsUnmeasuredOrInvalidFrameValues() {
        assertFalse(PerformanceRuntimeProofLogger.isValidProofSample(0, 16.0D));
        assertFalse(PerformanceRuntimeProofLogger.isValidProofSample(10, 0.0D));
        assertFalse(PerformanceRuntimeProofLogger.isValidProofSample(10, Double.NaN));
        assertFalse(PerformanceRuntimeProofLogger.isValidProofSample(10, Double.POSITIVE_INFINITY));
        assertTrue(PerformanceRuntimeProofLogger.isValidProofSample(1, 16.0D));
    }
}
