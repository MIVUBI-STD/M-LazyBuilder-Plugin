package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkUploadDrainPolicyTest {
    @Test
    void normalRenderPassUsesBoundedUploadBudget() {
        assertEquals(48, ChunkUploadDrainPolicy.taskBudget(false));
        assertTrue(ChunkUploadDrainPolicy.shouldContinue(47, false));
        assertFalse(ChunkUploadDrainPolicy.shouldContinue(48, false));
    }

    @Test
    void shutdownDrainsEverything() {
        assertEquals(Integer.MAX_VALUE, ChunkUploadDrainPolicy.taskBudget(true));
        assertTrue(ChunkUploadDrainPolicy.shouldContinue(Integer.MAX_VALUE - 1, true));
    }
}
