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
    void moveDragPreservesSelectionSize() {
        MapAreaSelectionState state = new MapAreaSelectionState();
        UUID worldId = UUID.randomUUID();
        state.activate(worldId, 2, 4, -3, -1);

        state.beginDrag(MapAreaSelectionState.DragMode.MOVE, 3, -2);
        state.updateDrag(8, 1);

        assertEquals(7, state.minChunkX);
        assertEquals(9, state.maxChunkX);
        assertEquals(0, state.minChunkZ);
        assertEquals(2, state.maxChunkZ);
    }

    @Test
    void edgeDragNormalizesCrossedBounds() {
        MapAreaSelectionState state = new MapAreaSelectionState();
        state.activate(UUID.randomUUID(), 0, 4, 0, 4);

        state.beginDrag(MapAreaSelectionState.DragMode.W, 0, 2);
        state.updateDrag(6, 2);

        assertEquals(4, state.minChunkX);
        assertEquals(6, state.maxChunkX);
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
