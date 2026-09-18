package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapExportWorkspaceLifecycleTest {
    @Test
    void enterResetsTransientRequestState() {
        MapExportWorkspaceLifecycle lifecycle = new MapExportWorkspaceLifecycle();
        lifecycle.enter();
        lifecycle.markSettingsRequested();
        lifecycle.markFormatsRequested();
        lifecycle.markControlsReady();
        lifecycle.scrollBy(60, 120);

        lifecycle.enter();

        assertTrue(lifecycle.active());
        assertFalse(lifecycle.requestedSettings());
        assertFalse(lifecycle.requestedFormats());
        assertFalse(lifecycle.controlsReady());
        assertEquals(0, lifecycle.scroll());
    }

    @Test
    void exitClearsPresentationLifecycle() {
        MapExportWorkspaceLifecycle lifecycle = new MapExportWorkspaceLifecycle();
        lifecycle.enter();
        lifecycle.markSettingsRequested();
        lifecycle.markFormatsRequested();
        lifecycle.markControlsReady();
        lifecycle.scrollBy(50, 100);

        lifecycle.exit();

        assertFalse(lifecycle.active());
        assertFalse(lifecycle.requestedSettings());
        assertFalse(lifecycle.requestedFormats());
        assertFalse(lifecycle.controlsReady());
        assertEquals(0, lifecycle.scroll());
    }

    @Test
    void scrollingRemainsBounded() {
        MapExportWorkspaceLifecycle lifecycle = new MapExportWorkspaceLifecycle();
        lifecycle.enter();

        lifecycle.scrollBy(80, 60);
        assertEquals(60, lifecycle.scroll());

        lifecycle.scrollBy(-100, 60);
        assertEquals(0, lifecycle.scroll());

        lifecycle.scrollBy(40, 100);
        lifecycle.clampScroll(20);
        assertEquals(20, lifecycle.scroll());
    }
}
