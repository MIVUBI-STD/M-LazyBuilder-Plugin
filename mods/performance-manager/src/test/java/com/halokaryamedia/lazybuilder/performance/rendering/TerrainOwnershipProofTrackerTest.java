package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainOwnershipProofTrackerTest {
    @Test
    void promotesOnlyStableSequentialResidents() {
        Object key = new Object();
        TerrainOwnershipProofTracker<Object> tracker = new TerrainOwnershipProofTracker<>();
        tracker.begin(key, false);

        for (int i = 0; i < TerrainOwnershipProofTracker.REQUIRED_STABLE_DRAWS - 1; i++) tracker.recordDraw(key);
        assertFalse(tracker.isCandidate(key));

        tracker.recordDraw(key);
        assertTrue(tracker.isCandidate(key));
        assertEquals(1, tracker.snapshot().candidateBuffers());
        assertEquals("candidate", tracker.snapshot().status());
    }

    @Test
    void relocationOrInvalidationResetsProof() {
        Object key = new Object();
        TerrainOwnershipProofTracker<Object> tracker = new TerrainOwnershipProofTracker<>();
        tracker.begin(key, false);
        for (int i = 0; i < 600; i++) tracker.recordDraw(key);
        assertTrue(tracker.isCandidate(key));

        tracker.reset(key);
        assertFalse(tracker.isCandidate(key));
        assertEquals(1L, tracker.snapshot().proofResets());

        tracker.invalidate(key);
        assertEquals(0, tracker.snapshot().observingBuffers());
        assertEquals(2L, tracker.snapshot().proofResets());
    }

    @Test
    void customIndexResidentsNeverBecomeExclusiveCandidates() {
        Object key = new Object();
        TerrainOwnershipProofTracker<Object> tracker = new TerrainOwnershipProofTracker<>();
        tracker.begin(key, true);
        for (int i = 0; i < 5000; i++) tracker.recordDraw(key);

        assertFalse(tracker.isCandidate(key));
        assertEquals(1, tracker.snapshot().excludedCustomIndexBuffers());
        assertEquals(0L, tracker.snapshot().successfulProofDraws());
        assertEquals("custom-index-excluded", tracker.snapshot().status());
    }
}
