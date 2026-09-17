package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainSubmissionPolicyTest {
    @Test
    void smallVisibleSetsKeepVanillaTraversal() {
        assertFalse(TerrainSubmissionPolicy.shouldUseIndex(63, 40));
    }

    @Test
    void sparseLayerPopulationUsesSubmissionIndex() {
        assertTrue(TerrainSubmissionPolicy.shouldUseIndex(100, 180));
    }

    @Test
    void denseLayerPopulationKeepsVanillaTraversal() {
        assertFalse(TerrainSubmissionPolicy.shouldUseIndex(100, 450));
    }

    @Test
    void reverseTraversalMapsEndIndexToFilteredListEnd() {
        assertEquals(25, TerrainSubmissionPolicy.mappedIteratorIndex(100, 100, 25));
        assertEquals(0, TerrainSubmissionPolicy.mappedIteratorIndex(0, 100, 25));
    }
}
