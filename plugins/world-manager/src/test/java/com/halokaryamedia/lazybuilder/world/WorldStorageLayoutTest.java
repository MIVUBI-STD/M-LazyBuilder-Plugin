package com.halokaryamedia.lazybuilder.world;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldStorageLayoutTest {
    @Test
    void resolvesCanonicalWorkspaceLayoutFromLauncherEnvironment() {
        Path workspace = Path.of("build", "workspace").toAbsolutePath().normalize();
        Path worlds = workspace.resolve("world-system").resolve("worlds");
        Path pluginData = workspace.resolve("server").resolve("plugins").resolve("World-Manager");

        WorldStorageLayout layout = WorldStorageLayout.resolve(worlds, pluginData, workspace.toString());

        assertTrue(layout.canonical());
        assertEquals(worlds, layout.worldsRoot());
        assertEquals(workspace.resolve("world-system").resolve("registry.yml"), layout.registryPath());
        assertEquals(workspace.resolve("world-system").resolve("imports"), layout.importsRoot());
        assertEquals(workspace.resolve("world-system").resolve("work").resolve("transfer"), layout.transferRoot());
        assertEquals(workspace.resolve("tools").resolve("lazybuilder").resolve("cache").resolve("converter"), layout.conversionRoot());
    }

    @Test
    void infersCanonicalWorkspaceWhenPaperAlreadyUsesWorldSystemWorlds() {
        Path workspace = Path.of("build", "workspace-inferred").toAbsolutePath().normalize();
        Path worlds = workspace.resolve("world-system").resolve("worlds");
        Path pluginData = workspace.resolve("server").resolve("plugins").resolve("World-Manager");

        WorldStorageLayout layout = WorldStorageLayout.resolve(worlds, pluginData, null);

        assertTrue(layout.canonical());
        assertEquals(workspace.resolve("world-system").resolve("backups"), layout.backupsRoot());
    }

    @Test
    void keepsLegacyPluginDataLayoutForManualNonCanonicalLaunches() {
        Path server = Path.of("build", "legacy-server").toAbsolutePath().normalize();
        Path pluginData = server.resolve("plugins").resolve("World-Manager");

        WorldStorageLayout layout = WorldStorageLayout.resolve(server, pluginData, null);

        assertFalse(layout.canonical());
        assertEquals(server, layout.worldsRoot());
        assertEquals(pluginData.resolve("world").resolve("registry.yml"), layout.registryPath());
        assertEquals(pluginData.resolve("world").resolve("runtime").resolve("converter"), layout.conversionRoot());
    }

    @Test
    void launcherEnvironmentFailsClosedWhenPaperUsesWrongWorldContainer() {
        Path workspace = Path.of("build", "workspace-mismatch").toAbsolutePath().normalize();
        Path wrongWorldContainer = workspace.resolve("server");
        Path pluginData = workspace.resolve("server").resolve("plugins").resolve("World-Manager");

        assertThrows(IllegalStateException.class,
                () -> WorldStorageLayout.resolve(wrongWorldContainer, pluginData, workspace.toString()));
    }
}
