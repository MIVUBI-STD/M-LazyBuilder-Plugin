package com.halokaryamedia.lazybuilder.utilities;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UtilitiesConfigurationContractTest {
    @Test
    void acceptsCanonicalConfigurationShape() {
        YamlConfiguration config = canonicalConfig();
        assertDoesNotThrow(() -> UtilitiesManagerPlugin.validateConfiguration(config));
    }

    @Test
    void rejectsWrongBooleanTypes() {
        YamlConfiguration config = canonicalConfig();
        config.set("features.movement.abilities.fly", "yes");

        assertThrows(IllegalArgumentException.class, () -> UtilitiesManagerPlugin.validateConfiguration(config));
    }

    @Test
    void rejectsIncludeScopeWithoutAnyWorld() {
        YamlConfiguration config = canonicalConfig();
        config.set("features.world-safety.scope.mode", "include");
        config.set("features.world-safety.scope.include-worlds", List.of("   "));

        assertThrows(IllegalArgumentException.class, () -> UtilitiesManagerPlugin.validateConfiguration(config));
    }

    @Test
    void acceptsIncludeScopeWithAtLeastOneWorld() {
        YamlConfiguration config = canonicalConfig();
        config.set("features.world-safety.scope.mode", "include");
        config.set("features.world-safety.scope.include-worlds", List.of("BuildWorld"));

        assertDoesNotThrow(() -> UtilitiesManagerPlugin.validateConfiguration(config));
    }

    private YamlConfiguration canonicalConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.createSection("features.world-safety");
        config.createSection("features.movement");
        config.createSection("features.build-helpers");
        config.set("features.world-safety.scope.mode", "all");
        config.set("features.world-safety.scope.include-worlds", List.of());
        config.set("features.world-safety.scope.exclude-worlds", List.of());
        config.set("features.movement.abilities.fly", true);
        return config;
    }
}
