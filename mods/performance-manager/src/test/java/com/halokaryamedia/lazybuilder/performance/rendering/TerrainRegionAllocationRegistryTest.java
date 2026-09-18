package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TerrainRegionAllocationRegistryTest {
    @Test
    void keepsStableAlignedHandlesWhilePayloadStillFits() {
        TerrainRegionAllocationRegistry<Object> registry = new TerrainRegionAllocationRegistry<>();
        Object a = new Object();
        Object b = new Object();

        registry.associate(a, 0, 0, 0, 0);
        registry.associate(b, 1, 0, 0, 0);
        registry.recordPayload(a, 1000L);
        registry.recordPayload(b, 500L);

        TerrainRegionAllocationRegistry.Handle first = registry.handle(a);
        assertNotNull(first);
        assertEquals(0L, first.offsetBytes());
        assertEquals(1024L, first.sizeBytes());
        assertEquals(0L, first.offsetBytes() % TerrainRegionArenaPolicy.SUBALLOCATION_ALIGNMENT);

        registry.recordPayload(a, 900L);
        TerrainRegionAllocationRegistry.Handle reused = registry.handle(a);
        assertEquals(first.offsetBytes(), reused.offsetBytes());
        assertEquals(first.sizeBytes(), reused.sizeBytes());
        assertEquals(first.generation(), reused.generation());
        assertEquals(1L, registry.snapshot().allocationReuses());
        assertEquals(1, registry.snapshot().activeArenas());
        assertEquals(2, registry.snapshot().activeAllocations());
    }

    @Test
    void growingPayloadReallocatesHandleAndCanGrowArena() {
        TerrainRegionAllocationRegistry<Object> registry = new TerrainRegionAllocationRegistry<>();
        Object a = new Object();
        Object b = new Object();

        registry.associate(a, 0, 0, 0, 0);
        registry.associate(b, 0, 0, 0, 0);
        registry.recordPayload(a, 700_000L);
        long originalGeneration = registry.handle(a).generation();
        registry.recordPayload(b, 700_000L);

        TerrainRegionAllocationRegistry.Snapshot afterGrowth = registry.snapshot();
        assertEquals(1, afterGrowth.activeArenas());
        assertEquals(2, afterGrowth.activeAllocations());
        assertEquals(1L, afterGrowth.arenaGrowths());
        assertEquals(0L, afterGrowth.allocationFailures());
        assertNotEquals(originalGeneration, registry.handle(a).generation());
    }

    @Test
    void fragmentedFreeSpaceCompactsBeforeGrowing() {
        TerrainRegionAllocationRegistry<Object> registry = new TerrainRegionAllocationRegistry<>();
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        Object d = new Object();

        registry.associate(a, 0, 0, 0, 0);
        registry.associate(b, 0, 0, 0, 0);
        registry.associate(c, 0, 0, 0, 0);
        registry.associate(d, 0, 0, 0, 0);
        registry.recordPayload(a, 262_144L);
        registry.recordPayload(b, 262_144L);
        registry.recordPayload(c, 262_144L);
        registry.release(b);
        registry.recordPayload(d, 300_000L);

        TerrainRegionAllocationRegistry.Snapshot snapshot = registry.snapshot();
        assertEquals(1L, snapshot.compactions());
        assertEquals(0L, snapshot.arenaGrowths());
        assertEquals(0L, snapshot.allocationFailures());
        assertNotNull(registry.handle(d));
        assertEquals(3, snapshot.activeAllocations());
    }

    @Test
    void crossingRegionOrLayerMovesOwnershipToDifferentArena() {
        TerrainRegionAllocationRegistry<Object> registry = new TerrainRegionAllocationRegistry<>();
        Object buffer = new Object();

        registry.associate(buffer, 0, 0, 0, 0);
        registry.recordPayload(buffer, 4096L);
        TerrainRegionAllocationRegistry.Handle first = registry.handle(buffer);

        registry.associate(buffer, 8, 0, 0, 0);
        TerrainRegionAllocationRegistry.Handle movedRegion = registry.handle(buffer);
        assertNotEquals(first.arenaKey(), movedRegion.arenaKey());
        assertEquals(1, registry.snapshot().activeArenas());

        registry.associate(buffer, 8, 0, 0, 3);
        TerrainRegionAllocationRegistry.Handle movedLayer = registry.handle(buffer);
        assertNotEquals(movedRegion.arenaKey(), movedLayer.arenaKey());
        assertEquals(3, movedLayer.arenaKey().layerSlot());
        assertEquals(1, registry.snapshot().activeArenas());
    }
}
