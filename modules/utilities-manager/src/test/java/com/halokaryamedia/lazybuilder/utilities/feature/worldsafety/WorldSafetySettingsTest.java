package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldSafetySettingsTest {
    @Test
    void defaultsEveryProtectionOnForAllWorlds() {
        YamlConfiguration config = new YamlConfiguration();
        WorldSafetySettings settings = WorldSafetySettings.from(config.createSection("world-safety"));

        assertTrue(settings.explosions());
        assertTrue(settings.leavesDecay());
        assertTrue(settings.farmlandTrample());
        assertTrue(settings.dragonEggTeleport());
        assertTrue(settings.appliesTo("world"));
    }

    @Test
    void readsProtectionsIndependently() {
        YamlConfiguration config = new YamlConfiguration();
        var section = config.createSection("world-safety");
        section.set("protections.explosions", false);
        section.set("protections.leaves-decay", true);
        section.set("protections.farmland-trample", false);
        section.set("protections.dragon-egg-teleport", true);

        WorldSafetySettings settings = WorldSafetySettings.from(section);

        assertFalse(settings.explosions());
        assertTrue(settings.leavesDecay());
        assertFalse(settings.farmlandTrample());
        assertTrue(settings.dragonEggTeleport());
    }

    @Test
    void supportsIncludeAndExcludeWorldScopeWithTrimmedCaseInsensitiveNames() {
        YamlConfiguration config = new YamlConfiguration();
        var section = config.createSection("world-safety");
        section.set("scope.mode", " INCLUDE ");
        section.set("scope.include-worlds", java.util.List.of(" BuildWorld ", "TestWorld", "   "));
        section.set("scope.exclude-worlds", java.util.List.of(" testworld "));

        WorldSafetySettings settings = WorldSafetySettings.from(section);

        assertTrue(settings.appliesTo("buildworld"));
        assertTrue(settings.appliesTo(" BuildWorld "));
        assertFalse(settings.appliesTo("testworld"));
        assertFalse(settings.appliesTo("survival"));
        assertFalse(settings.includeWorlds().contains(""));
    }
}
