package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuStageTimerTest {
    @BeforeEach
    void reset() {
        GpuStageTimer.resetForTest();
    }

    @Test
    void sessionResetPreservesInstrumentationPolicyAndClearsEvidence() {
        GpuStageTimer.setEnabledForTest(true);

        GpuStageTimer.resetSession();

        assertTrue(GpuStageTimer.enabled());
        assertEquals(0L, GpuStageTimer.snapshot(GpuStageTimer.Stage.TERRAIN).samples());
        assertEquals(0L, GpuStageTimer.snapshot(GpuStageTimer.Stage.SHADOW).samples());
        assertEquals(0L, GpuStageTimer.snapshot(GpuStageTimer.Stage.POST_PROCESS).samples());
    }
}
