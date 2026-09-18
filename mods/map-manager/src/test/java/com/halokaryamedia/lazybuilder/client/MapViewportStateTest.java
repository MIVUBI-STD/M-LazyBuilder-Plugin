package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MapViewportStateTest {
    @Test
    void panUsesCurrentZoomScale() {
        MapViewportState state = new MapViewportState();
        state.centerOn(100.0, -20.0);
        state.zoom = 4.0;

        state.panByPixels(5.0, -3.0);

        assertEquals(90.0, state.centerX);
        assertEquals(-14.0, state.centerZ);
    }

    @Test
    void screenCoordinatesResolveAroundViewportCenter() {
        MapViewportState state = new MapViewportState();
        state.centerOn(-32.0, 48.0);
        state.zoom = 2.0;

        assertArrayEquals(new double[] {-22.0, 43.0}, state.worldAtScreen(110, 95, 100, 100), 0.0001);
    }
}
