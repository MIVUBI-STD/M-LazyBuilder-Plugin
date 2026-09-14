package com.halokaryamedia.lazybuilder.utilities;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginCommandContractTest {
    @Test
    void declaresFamiliarBuilderCommandsAndRolePermissions() throws Exception {
        var stream = PluginCommandContractTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);

        YamlConfiguration pluginYml = new YamlConfiguration();
        pluginYml.loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

        for (String command : java.util.List.of("lb", "gmc", "gms", "gma", "gmsp", "fly", "noclip", "nightvision")) {
            assertNotNull(pluginYml.getConfigurationSection("commands." + command), command);
        }
        assertTrue(pluginYml.getStringList("commands.nightvision.aliases").contains("nv"));
        assertTrue(pluginYml.getBoolean("permissions.lazybuilder.utilities.help.default"));
        assertTrue(pluginYml.getBoolean("permissions.lazybuilder.builder.children.lazybuilder.utilities.fly"));
        assertTrue(pluginYml.getBoolean("permissions.lazybuilder.builder.children.lazybuilder.utilities.build"));
        assertTrue(pluginYml.getBoolean("permissions.lazybuilder.admin.children.lazybuilder.utilities.reload"));
    }
}
