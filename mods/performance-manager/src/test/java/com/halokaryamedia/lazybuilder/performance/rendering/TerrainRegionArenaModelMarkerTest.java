package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the arena model classes linked into test discovery until physical GPU wiring lands. */
final class TerrainRegionArenaModelMarkerTest {
    @Test
    void arenaPolicyAndSuballocatorRemainAvailable() {
        assertTrue(TerrainRegionArenaPolicy.SUBALLOCATION_ALIGNMENT > 0L);
        assertTrue(new TerrainRegionSuballocator<>(1024L).snapshot().freeBytes() == 1024L);
    }
}
