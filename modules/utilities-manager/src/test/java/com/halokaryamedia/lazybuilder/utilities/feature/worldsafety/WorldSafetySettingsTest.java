package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldSafetySettingsTest {
    @Test
    void defaultsEveryProtectionOn() {
        YamlConfiguration config = new YamlConfiguration();
        WorldSafetySettings settings = WorldSafetySettings.from(config.createSection("world-safety"));

        assertTrue(settings.explosions());
        assertTrue(settings.leavesDecay());
        assertTrue(settings.farmlandTrample());
        assertTrue(settings.dragonEggTeleport());
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
}
