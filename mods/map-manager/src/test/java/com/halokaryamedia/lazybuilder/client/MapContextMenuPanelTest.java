package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapContextMenuPanelTest {
    @Test
    void ownsTargetAndMapsRowsToActions() {
        MapContextMenuPanel panel = new MapContextMenuPanel();
        panel.open(-96, -80, 300, 200);

        assertTrue(panel.isOpen());
        assertEquals(-96, panel.blockX());
        assertEquals(-80, panel.blockZ());
        assertEquals(
                MapContextMenuPanel.Action.TELEPORT,
                panel.actionAt(100, 0, 900, 600, 320, 240));
        assertEquals(
                MapContextMenuPanel.Action.EXPORT_AREA,
                panel.actionAt(100, 0, 900, 600, 320, 265));
        assertEquals(
                MapContextMenuPanel.Action.COPY_COORDINATES,
                panel.actionAt(100, 0, 900, 600, 320, 290));
    }

    @Test
    void outsideClickDismissesAndCloseDropsOpenState() {
        MapContextMenuPanel panel = new MapContextMenuPanel();
        panel.open(12, 34, 300, 200);

        assertEquals(
                MapContextMenuPanel.Action.DISMISS,
                panel.actionAt(100, 0, 900, 600, 120, 120));

        panel.close();

        assertFalse(panel.isOpen());
        assertEquals(
                MapContextMenuPanel.Action.NONE,
                panel.actionAt(100, 0, 900, 600, 320, 240));
    }

    @Test
    void menuClampsNearViewportEdgeBeforeHitTesting() {
        MapContextMenuPanel panel = new MapContextMenuPanel();
        panel.open(1, 2, 895, 595);

        // Clamped menu origin is x=745, y=487 for this viewport.
        assertEquals(
                MapContextMenuPanel.Action.TELEPORT,
                panel.actionAt(100, 0, 900, 600, 760, 525));
    }
}
