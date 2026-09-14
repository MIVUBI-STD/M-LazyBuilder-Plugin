package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementSettingsTest {
    @Test
    void defaultsEveryAbilityOnWhenAbilitySectionIsAbsent() {
        MemoryConfiguration section = new MemoryConfiguration();

        MovementSettings settings = MovementSettings.from(section);

        assertTrue(settings.fly());
        assertTrue(settings.noclip());
        assertTrue(settings.nightVision());
    }

    @Test
    void readsEachAbilityIndependently() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("abilities.fly", false);
        section.set("abilities.noclip", true);
        section.set("abilities.night-vision", false);

        MovementSettings settings = MovementSettings.from(section);

        assertFalse(settings.fly());
        assertTrue(settings.noclip());
        assertFalse(settings.nightVision());
    }

    @Test
    void readsLegacyAdvancedFlyKeyWhenNewKeyIsAbsent() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("abilities.advanced-fly", false);

        MovementSettings settings = MovementSettings.from(section);

        assertFalse(settings.fly());
    }
}
