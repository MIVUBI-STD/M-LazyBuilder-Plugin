package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMapSurfaceCacheTest {
    @Test
    void failedWriteKeepsRegionDirtyAndRetryable() {
        ClientMapSurfaceCache.RegionData region = new ClientMapSurfaceCache.RegionData();

        assertTrue(region.put(7, 0xFF112233, 80));
        assertTrue(region.isDirty());

        ClientMapSurfaceCache.RegionSnapshot snapshot = region.snapshot();
        assertTrue(region.markWriteQueued(snapshot.revision()));
        region.completeWrite(snapshot.revision(), false);

        assertTrue(region.isDirty());
        assertTrue(region.shouldQueueWrite());
        assertEquals(0L, region.persistedRevision());
    }

    @Test
    void successfulWriteOnlyPersistsTheWrittenRevision() {
        ClientMapSurfaceCache.RegionData region = new ClientMapSurfaceCache.RegionData();
        region.put(7, 0xFF112233, 80);
        ClientMapSurfaceCache.RegionSnapshot first = region.snapshot();
        assertTrue(region.markWriteQueued(first.revision()));

        region.put(8, 0xFF445566, 81);
        region.completeWrite(first.revision(), true);

        assertTrue(region.isDirty());
        assertEquals(first.revision(), region.persistedRevision());
        assertTrue(region.shouldQueueWrite());

        ClientMapSurfaceCache.RegionSnapshot second = region.snapshot();
        assertTrue(region.markWriteQueued(second.revision()));
        region.completeWrite(second.revision(), true);

        assertFalse(region.isDirty());
        assertEquals(second.revision(), region.persistedRevision());
    }

    @Test
    void mergingLoadedDataDoesNotCreateDirtyRevision() {
        ClientMapSurfaceCache.RegionData region = new ClientMapSurfaceCache.RegionData();

        assertTrue(region.putIfAbsent(3, 0xFFAABBCC, 64));

        assertFalse(region.isDirty());
        assertEquals(0L, region.revision());
        assertEquals(1, region.size());
    }

    @Test
    void cleanRegionEvictionRespectsBoundAndActiveRegion() {
        LinkedHashMap<Long, ClientMapSurfaceCache.RegionData> regions =
                new LinkedHashMap<>(128, 0.75f, true);
        ClientMapSurfaceCache.RegionData active = null;

        for (long key = 0; key < 100; key++) {
            ClientMapSurfaceCache.RegionData region = new ClientMapSurfaceCache.RegionData();
            region.putIfAbsent((int) key, 0xFF112233, 64);
            regions.put(key, region);
            if (key == 0) active = region;
        }

        int removedSamples = ClientMapSurfaceCache.pruneCleanRegions(regions, 96, active);

        assertEquals(96, regions.size());
        assertEquals(4, removedSamples);
        assertTrue(regions.containsValue(active));
    }

    @Test
    void dirtyRegionIsNeverEvictedBeforePersistence() {
        LinkedHashMap<Long, ClientMapSurfaceCache.RegionData> regions =
                new LinkedHashMap<>(8, 0.75f, true);

        ClientMapSurfaceCache.RegionData dirty = new ClientMapSurfaceCache.RegionData();
        dirty.put(0, 0xFF445566, 70);
        regions.put(0L, dirty);

        for (long key = 1; key <= 4; key++) {
            ClientMapSurfaceCache.RegionData clean = new ClientMapSurfaceCache.RegionData();
            clean.putIfAbsent((int) key, 0xFF778899, 71);
            regions.put(key, clean);
        }

        int removedSamples = ClientMapSurfaceCache.pruneCleanRegions(regions, 3, null);

        assertEquals(3, regions.size());
        assertEquals(2, removedSamples);
        assertTrue(regions.containsValue(dirty));
        assertTrue(dirty.isDirty());
    }

    @Test
    void regionWithWriteInFlightIsNotEvicted() {
        LinkedHashMap<Long, ClientMapSurfaceCache.RegionData> regions =
                new LinkedHashMap<>(8, 0.75f, true);

        ClientMapSurfaceCache.RegionData writing = new ClientMapSurfaceCache.RegionData();
        writing.put(0, 0xFF335577, 72);
        ClientMapSurfaceCache.RegionSnapshot snapshot = writing.snapshot();
        assertTrue(writing.markWriteQueued(snapshot.revision()));
        regions.put(0L, writing);

        for (long key = 1; key <= 4; key++) {
            ClientMapSurfaceCache.RegionData clean = new ClientMapSurfaceCache.RegionData();
            clean.putIfAbsent((int) key, 0xFF7799BB, 73);
            regions.put(key, clean);
        }

        int removedSamples = ClientMapSurfaceCache.pruneCleanRegions(regions, 3, null);

        assertEquals(3, regions.size());
        assertEquals(2, removedSamples);
        assertTrue(regions.containsValue(writing));
        assertTrue(writing.hasWritesInFlight());
    }

    @Test
    void staleAsyncCompletionGenerationIsRejected() {
        assertTrue(ClientMapSurfaceCache.isCurrentScopeGeneration(7L, 7L));
        assertFalse(ClientMapSurfaceCache.isCurrentScopeGeneration(6L, 7L));
        assertFalse(ClientMapSurfaceCache.isCurrentScopeGeneration(8L, 7L));
    }

    @Test
    void farZoomSampleCoordinateRemainsStableInsideWorldCell() {
        assertEquals(18, ClientMapSurfaceCache.stableSampleCoordinate(16, 4));
        assertEquals(18, ClientMapSurfaceCache.stableSampleCoordinate(17, 4));
        assertEquals(18, ClientMapSurfaceCache.stableSampleCoordinate(18, 4));
        assertEquals(18, ClientMapSurfaceCache.stableSampleCoordinate(19, 4));
        assertEquals(22, ClientMapSurfaceCache.stableSampleCoordinate(20, 4));
    }

    @Test
    void farZoomSampleCoordinateUsesFloorDivisionForNegativeWorldCoordinates() {
        assertEquals(-2, ClientMapSurfaceCache.stableSampleCoordinate(-1, 4));
        assertEquals(-2, ClientMapSurfaceCache.stableSampleCoordinate(-4, 4));
        assertEquals(-6, ClientMapSurfaceCache.stableSampleCoordinate(-5, 4));
        assertEquals(-1, ClientMapSurfaceCache.stableSampleCoordinate(-1, 1));
    }

    @Test
    void netherVerticalBandRemainsStableForSixteenBlockSlice() {
        assertEquals(8, ClientMapSurfaceCache.netherLayerCenter(0));
        assertEquals(8, ClientMapSurfaceCache.netherLayerCenter(7));
        assertEquals(8, ClientMapSurfaceCache.netherLayerCenter(15));
        assertEquals(24, ClientMapSurfaceCache.netherLayerCenter(16));
        assertEquals(24, ClientMapSurfaceCache.netherLayerCenter(31));
    }

    @Test
    void netherVerticalBandUsesFloorDivisionBelowZero() {
        assertEquals(-8, ClientMapSurfaceCache.netherLayerCenter(-1));
        assertEquals(-8, ClientMapSurfaceCache.netherLayerCenter(-16));
        assertEquals(-24, ClientMapSurfaceCache.netherLayerCenter(-17));
    }
    @Test
    void failedRegionLoadsRetryOnlyWithinBoundedAttemptBudget() {
        assertTrue(ClientMapSurfaceCache.retryRegionLoadAllowed(0));
        assertTrue(ClientMapSurfaceCache.retryRegionLoadAllowed(1));
        assertTrue(ClientMapSurfaceCache.retryRegionLoadAllowed(2));
        assertFalse(ClientMapSurfaceCache.retryRegionLoadAllowed(3));
        assertFalse(ClientMapSurfaceCache.retryRegionLoadAllowed(4));
        assertFalse(ClientMapSurfaceCache.retryRegionLoadAllowed(-1));
    }

}
