package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkUploadDrainPolicyTest {
    @Test
    void uploadBudgetShrinksWithFramePressure() {
        assertEquals(48, ChunkUploadDrainPolicy.taskBudget(false, FramePressure.NORMAL));
        assertEquals(24, ChunkUploadDrainPolicy.taskBudget(false, FramePressure.ELEVATED));
        assertEquals(8, ChunkUploadDrainPolicy.taskBudget(false, FramePressure.HEAVY));
        assertTrue(ChunkUploadDrainPolicy.shouldContinue(23, false, FramePressure.ELEVATED));
        assertFalse(ChunkUploadDrainPolicy.shouldContinue(24, false, FramePressure.ELEVATED));
    }

    @Test
    void shutdownDrainsEverything() {
        assertEquals(Integer.MAX_VALUE, ChunkUploadDrainPolicy.taskBudget(true, FramePressure.HEAVY));
        assertTrue(ChunkUploadDrainPolicy.shouldContinue(Integer.MAX_VALUE - 1, true, FramePressure.HEAVY));
    }
}
