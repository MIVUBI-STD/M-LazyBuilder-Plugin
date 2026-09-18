package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChunkRebuildBackpressurePolicyTest {
    @Test
    void defersOnlyNonPrioritizedWorkUnderRealHeavyBacklog() {
        assertTrue(ChunkRebuildBackpressurePolicy.shouldDefer(
                FramePressure.HEAVY, false, 8, 1, 0
        ));
        assertFalse(ChunkRebuildBackpressurePolicy.shouldDefer(
                FramePressure.HEAVY, true, 32, 0, 0
        ));
        assertFalse(ChunkRebuildBackpressurePolicy.shouldDefer(
                FramePressure.ELEVATED, false, 32, 0, 0
        ));
        assertFalse(ChunkRebuildBackpressurePolicy.shouldDefer(
                FramePressure.HEAVY, false, 7, 0, 0
        ));
        assertFalse(ChunkRebuildBackpressurePolicy.shouldDefer(
                FramePressure.HEAVY, false, 32, 2, 0
        ));
    }

    @Test
    void releaseBudgetAlwaysMakesProgress() {
        assertEquals(16, ChunkRebuildBackpressurePolicy.releaseBudget(FramePressure.NORMAL));
        assertEquals(4, ChunkRebuildBackpressurePolicy.releaseBudget(FramePressure.ELEVATED));
        assertEquals(1, ChunkRebuildBackpressurePolicy.releaseBudget(FramePressure.HEAVY));
    }
}
