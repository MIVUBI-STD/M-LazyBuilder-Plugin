package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MapAreaSelectionOverlayTest {
    @Test
    void hitTestingUsesNegativeWorldCoordinatesThroughViewport() {
        MapAreaSelectionState selection = new MapAreaSelectionState();
        selection.activate(UUID.randomUUID(), -6, -1, -5, -2);

        MapAreaSelectionOverlay overlay = new MapAreaSelectionOverlay();
        MapAreaSelectionOverlay.Viewport viewport = new MapAreaSelectionOverlay.Viewport(
                100, 0, 900, 600,
                500, 300,
                -96.0, -80.0,
                1.0);

        assertEquals(
                MapAreaSelectionState.DragMode.NW,
                overlay.hit(selection, viewport, 500, 300));
        assertEquals(
                MapAreaSelectionState.DragMode.MOVE,
                overlay.hit(selection, viewport, 548, 332));
    }

    @Test
    void inactiveSelectionNeverCapturesPointer() {
        MapAreaSelectionState selection = new MapAreaSelectionState();
        MapAreaSelectionOverlay overlay = new MapAreaSelectionOverlay();
        MapAreaSelectionOverlay.Viewport viewport = new MapAreaSelectionOverlay.Viewport(
                0, 0, 800, 600,
                400, 300,
                0.0, 0.0,
                1.0);

        assertEquals(
                MapAreaSelectionState.DragMode.NONE,
                overlay.hit(selection, viewport, 400, 300));
    }
}
