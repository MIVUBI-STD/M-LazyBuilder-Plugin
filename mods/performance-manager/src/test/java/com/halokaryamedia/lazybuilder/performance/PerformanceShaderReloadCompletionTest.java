package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevent obsolete shader Futures from resetting a current in-flight reload. */
final class PerformanceShaderReloadCompletionTest {
    @Test
    void onlyExactOwnerAndGenerationCanCompleteTheCurrentReload() {
        Object owner = new Object();
        Object previousOwner = new Object();

        assertTrue(PerformanceManagerClient.isCurrentShaderReloadCompletion(12L, 12L, owner, owner));
        assertFalse(PerformanceManagerClient.isCurrentShaderReloadCompletion(13L, 12L, owner, owner));
        assertFalse(PerformanceManagerClient.isCurrentShaderReloadCompletion(12L, 12L, owner, previousOwner));
        assertFalse(PerformanceManagerClient.isCurrentShaderReloadCompletion(-1L, 12L, owner, owner));
        assertFalse(PerformanceManagerClient.isCurrentShaderReloadCompletion(12L, 12L, null, owner));
    }
}
