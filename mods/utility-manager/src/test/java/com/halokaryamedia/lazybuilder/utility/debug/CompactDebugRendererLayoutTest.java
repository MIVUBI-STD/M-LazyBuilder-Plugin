package com.halokaryamedia.lazybuilder.utility.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactDebugRendererLayoutTest {
    @Test
    void wideGuiKeepsServerPanelOnTheRight() {
        assertFalse(CompactDebugRenderer.shouldStackServer(720, 170, 160));
    }

    @Test
    void narrowGuiStacksServerBelowLeftColumn() {
        assertTrue(CompactDebugRenderer.shouldStackServer(310, 170, 160));
    }

    @Test
    void exactNonOverlapBoundaryStaysSideBySide() {
        assertFalse(CompactDebugRenderer.shouldStackServer(347, 170, 160));
        assertTrue(CompactDebugRenderer.shouldStackServer(346, 170, 160));
    }
}
