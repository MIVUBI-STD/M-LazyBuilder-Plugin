package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapAreaSelectionStateTest {
    @Test
    void activationNormalizesBoundsAndKeepsWorldOwnership() {
        MapAreaSelectionState state = new MapAreaSelectionState();
        UUID worldId = UUID.randomUUID();

        state.activate(worldId, 4, -2, 7, 3);

        assertTrue(state.ownsWorld(worldId));
        state.active = false;
        assertTrue(state.ownsWorld(worldId));
        assertEquals(-2, state.minChunkX);
        assertEquals(4, state.maxChunkX);
        assertEquals(3, state.minChunkZ);
        assertEquals(7, state.maxChunkZ);
        assertEquals(-32, state.minBlockX(16));
        assertEquals(79, state.maxBlockX(16));
    }

    @Test
    void clearDropsSelectionIdentity() {
        MapAreaSelectionState state = new MapAreaSelectionState();
        UUID worldId = UUID.randomUUID();
        state.activate(worldId, 0, 0, 0, 0);

        state.clear();

        assertFalse(state.active);
        assertFalse(state.ownsWorld(worldId));
    }
}
