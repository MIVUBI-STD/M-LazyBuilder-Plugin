package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkRebuildPolicyTest {
    @Test
    void firstRebuildRequestAlwaysRuns() {
        assertFalse(ChunkRebuildPolicy.shouldSkip(false, false, false));
        assertFalse(ChunkRebuildPolicy.shouldSkip(false, false, true));
    }

    @Test
    void duplicateNormalRequestIsCoalesced() {
        assertTrue(ChunkRebuildPolicy.shouldSkip(true, false, false));
    }

    @Test
    void importantRequestUpgradesPendingNormalRebuild() {
        assertFalse(ChunkRebuildPolicy.shouldSkip(true, false, true));
    }

    @Test
    void importantPendingRebuildAbsorbsEveryDuplicate() {
        assertTrue(ChunkRebuildPolicy.shouldSkip(true, true, false));
        assertTrue(ChunkRebuildPolicy.shouldSkip(true, true, true));
    }
}
