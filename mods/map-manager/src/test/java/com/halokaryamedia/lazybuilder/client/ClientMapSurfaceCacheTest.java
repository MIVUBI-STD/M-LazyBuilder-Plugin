package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

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
}
