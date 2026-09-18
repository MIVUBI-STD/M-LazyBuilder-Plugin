package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapExportWorkspacePanelTest {
    @TempDir Path tempDir;
    private final MapExportWorkspacePanel panel = new MapExportWorkspacePanel();

    @Test
    void sidebarWidthStaysWithinResponsiveBounds() {
        assertEquals(218, panel.sidebarWidth(500));
        assertEquals(254, panel.sidebarWidth(1440));
        assertEquals(218, panel.panelRect(500, 400).width());
        assertEquals(254, panel.panelRect(1440, 900).width());
    }

    @Test
    void coreActionsResolveFromPanelGeometry() {
        MapExportWorkspaceState state = state();
        int width = 1440;
        int height = 900;
        int left = panel.panelRect(width, height).left();

        assertEquals(
                MapExportWorkspacePanel.Action.BACK,
                panel.actionAt(state, false, width, height, left + 20, 16));
        assertEquals(
                MapExportWorkspacePanel.Action.FULL_SCOPE,
                panel.actionAt(state, true, width, height, left + 20, 62));
        assertEquals(
                MapExportWorkspacePanel.Action.AREA_SCOPE,
                panel.actionAt(state, true, width, height, left + 150, 62));
        assertEquals(
                MapExportWorkspacePanel.Action.CYCLE_FORMAT,
                panel.actionAt(state, true, width, height, left + 20, 130));
        assertEquals(
                MapExportWorkspacePanel.Action.SUBMIT,
                panel.actionAt(state, true, width, height, left + 20, height - 24));
    }

    @Test
    void advancedActionsFollowWorkspaceExpansion() {
        MapExportWorkspaceState state = state();
        int width = 1440;
        int height = 900;
        int left = panel.panelRect(width, height).left();

        assertEquals(
                MapExportWorkspacePanel.Action.TOGGLE_WORLD_SETTINGS,
                panel.actionAt(state, true, width, height, left + 20, 190));

        state.toggleWorldSettings();

        assertEquals(
                MapExportWorkspacePanel.Action.USE_CURRENT_POSITION,
                panel.actionAt(state, true, width, height, left + 20, 240));
    }

    @Test
    void narrowViewportProducesBoundedAdvancedScroll() {
        MapExportWorkspaceState state = state();
        state.toggleWorldSettings();

        int maxScroll = panel.maxScroll(state, 520, 220);

        assertTrue(maxScroll >= 0);
        state.scrollBy(10_000, maxScroll);
        assertEquals(maxScroll, state.scroll());
    }
    private MapExportWorkspaceState state() {
        WorldTransferPreferences preferences = new WorldTransferPreferences(
                tempDir.resolve("panel-transfer-preferences.properties"),
                () -> ClientServerIdentity.encode("panel-test.example:25565")
        );
        return new MapExportWorkspaceState(preferences);
    }
}
