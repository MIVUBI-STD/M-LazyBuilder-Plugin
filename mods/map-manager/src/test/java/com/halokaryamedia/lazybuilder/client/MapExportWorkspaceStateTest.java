package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapExportWorkspaceStateTest {
    @TempDir Path tempDir;

    @Test
    void sameWorldRevalidatesFormatAgainstFreshServerCatalog() {
        UUID worldId = UUID.randomUUID();
        MapExportWorkspaceState state = state();
        WorldControlWireProtocol.SettingsSnapshot source = settings(worldId, "CREATIVE", "NORMAL");

        List<String> initialFormats = List.of("JAVA_1_21_4", "BEDROCK_1_21_80");
        state.initialize(worldId, "Build", source, initialFormats);
        String previous = state.format();
        String replacement = previous.equalsIgnoreCase("JAVA_1_21_4")
                ? "BEDROCK_1_21_80"
                : "JAVA_1_21_4";
        assertNotEquals(previous, replacement);

        state.initialize(worldId, "Build", source, List.of(replacement));

        assertEquals(replacement, state.format());
    }

    @Test
    void enterAndExitResetTransientPresentationLifecycle() {
        MapExportWorkspaceState state = state();

        state.enter();
        state.markSettingsRequested();
        state.markFormatsRequested();
        state.markControlsReady();
        state.scrollBy(60, 120);

        state.enter();

        assertTrue(state.active());
        assertFalse(state.requestedSettings());
        assertFalse(state.requestedFormats());
        assertFalse(state.controlsReady());
        assertEquals(0, state.scroll());

        state.markSettingsRequested();
        state.markFormatsRequested();
        state.markControlsReady();
        state.scrollBy(40, 100);
        state.exit();

        assertFalse(state.active());
        assertFalse(state.requestedSettings());
        assertFalse(state.requestedFormats());
        assertFalse(state.controlsReady());
        assertEquals(0, state.scroll());
    }

    @Test
    void workspaceScrollRemainsBounded() {
        MapExportWorkspaceState state = state();
        state.enter();

        state.scrollBy(80, 60);
        assertEquals(60, state.scroll());

        state.scrollBy(-100, 60);
        assertEquals(0, state.scroll());

        state.scrollBy(40, 100);
        state.clampScroll(20);
        assertEquals(20, state.scroll());
    }

    @Test
    void workspaceInheritsGameModeAndDifficultyWhenNoControlsExposeOverrides() {
        UUID worldId = UUID.randomUUID();
        MapExportWorkspaceState state = state();
        state.initialize(
                worldId,
                "Survival Build",
                settings(worldId, "SURVIVAL", "HARD"),
                List.of("JAVA_1_21_4")
        );

        ExportSettingsWire.Settings wire = state.toWire();

        assertEquals("", wire.gameMode());
        assertEquals("", wire.difficulty());
        assertNull(wire.spawnX());
        assertNull(wire.spawnY());
        assertNull(wire.spawnZ());
        assertTrue(wire.optimizeOutput());
    }

    private MapExportWorkspaceState state() {
        WorldTransferPreferences preferences = new WorldTransferPreferences(
                tempDir.resolve("transfer-preferences.properties"),
                () -> ClientServerIdentity.encode("workspace-test.example:25565")
        );
        return new MapExportWorkspaceState(preferences);
    }

    private static WorldControlWireProtocol.SettingsSnapshot settings(
            UUID worldId,
            String gameMode,
            String difficulty
    ) {
        return new WorldControlWireProtocol.SettingsSnapshot(
                worldId,
                gameMode,
                difficulty,
                false,
                "CLEAR",
                6000L,
                0.0,
                64.0,
                0.0,
                List.of()
        );
    }
}
