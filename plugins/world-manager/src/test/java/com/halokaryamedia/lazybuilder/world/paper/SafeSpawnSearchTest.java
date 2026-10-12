package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the bounded search policy without requiring a Minecraft runtime. */
class SafeSpawnSearchTest {
    @Test
    void keepsExistingSafeSpawn() {
        var result = SafeSpawnSearch.nearby(10, 64, -5, -63, 318,
                (x, y, z) -> x == 10 && y == 64 && z == -5);
        assertEquals(new SafeSpawnSearch.Position(10, 64, -5), result.orElseThrow());
    }

    @Test
    void findsNearLandingWithoutMovingWorldSpawnOrSearchingFarAway() {
        var result = SafeSpawnSearch.nearby(10, 64, -5, -63, 318,
                (x, y, z) -> x == 11 && y == 64 && z == -5);
        assertEquals(new SafeSpawnSearch.Position(11, 64, -5), result.orElseThrow());
    }

    @Test
    void checksNearbyVerticalClearanceBeforeMovingFarHorizontally() {
        var result = SafeSpawnSearch.nearby(10, 64, -5, -63, 318,
                (x, y, z) -> (x == 10 && y == 65 && z == -5)
                        || (x == 11 && y == 64 && z == -5));
        assertEquals(new SafeSpawnSearch.Position(10, 65, -5), result.orElseThrow());
    }

    @Test
    void refusesUnboundedSearchOrInvalidHeightRange() {
        AtomicInteger inspected = new AtomicInteger();
        var result = SafeSpawnSearch.nearby(10, 64, -5, -63, 318,
                (x, y, z) -> { inspected.incrementAndGet(); return x == 15; });
        assertFalse(result.isPresent());
        assertTrue(inspected.get() <= 7 * 7 * 7);

        int previous = inspected.get();
        assertFalse(SafeSpawnSearch.nearby(10, 64, -5, 100, 20,
                (x, y, z) -> { inspected.incrementAndGet(); return true; }).isPresent());
        assertEquals(previous, inspected.get());
    }

    @Test
    void surfaceFallbackUsesOnlyNearestSafeColumn() {
        var result = SafeSpawnSearch.surface(10, -5, -63, 318,
                (x, z) -> 80,
                (x, y, z) -> x == 9 && y == 80 && z == -5);
        assertEquals(new SafeSpawnSearch.Position(9, 80, -5), result.orElseThrow());
    }
}
