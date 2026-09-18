package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldPermissionNodesTest {
    @Test
    void packagedPluginPermissionsMatchCanonicalNodes() {
        var stream = WorldPermissionNodesTest.class.getResourceAsStream("/plugin.yml");
        assertNotNull(stream, "packaged plugin.yml must exist");

        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(plugin.contains("permissions." + WorldPermissionNodes.MANAGE),
                "plugin.yml must declare the canonical manage permission");
        assertTrue(plugin.contains("permissions." + WorldPermissionNodes.TELEPORT),
                "plugin.yml must declare the canonical teleport permission");
    }
}
