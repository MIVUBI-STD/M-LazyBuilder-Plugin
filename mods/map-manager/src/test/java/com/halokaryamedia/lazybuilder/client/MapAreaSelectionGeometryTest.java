package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.MapAreaSelectionState.DragMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MapAreaSelectionGeometryTest {
    @Test
    void rectNormalizesBothAxes() {
        var rect = MapAreaSelectionGeometry.rect(120, 90, 40, 20);

        assertEquals(40, rect.left());
        assertEquals(20, rect.top());
        assertEquals(120, rect.right());
        assertEquals(90, rect.bottom());
    }

    @Test
    void handleHitWinsOverMoveHit() {
        var rect = MapAreaSelectionGeometry.rect(10, 10, 110, 90);

        assertEquals(DragMode.NW, MapAreaSelectionGeometry.hit(rect, 10, 10, 5));
        assertEquals(DragMode.E, MapAreaSelectionGeometry.hit(rect, 110, 50, 5));
        assertEquals(DragMode.MOVE, MapAreaSelectionGeometry.hit(rect, 60, 50, 5));
    }

    @Test
    void outsideSelectionReturnsNone() {
        var rect = MapAreaSelectionGeometry.rect(10, 10, 110, 90);

        assertEquals(DragMode.NONE, MapAreaSelectionGeometry.hit(rect, 150, 50, 5));
    }
}
