package com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildHelpersSettingsTest {
    @Test
    void defaultsEnableAllHelpersAndSafeSneakGuards() {
        YamlConfiguration config = new YamlConfiguration();
        BuildHelpersSettings settings = BuildHelpersSettings.from(config.createSection("build-helpers"));

        assertTrue(settings.ironDoorToggle());
        assertTrue(settings.doubleSlabBreak());
        assertTrue(settings.glazedTerracottaRotate());
        assertTrue(settings.requireSneakForSlab());
        assertTrue(settings.requireSneakForRotate());
    }

    @Test
    void readsEachHelperIndependently() {
        YamlConfiguration config = new YamlConfiguration();
        var section = config.createSection("build-helpers");
        section.set("helpers.iron-door-toggle", false);
        section.set("helpers.double-slab-break", true);
        section.set("helpers.glazed-terracotta-rotate", false);
        section.set("interaction.require-sneak-for-slab", false);
        section.set("interaction.require-sneak-for-rotate", true);

        BuildHelpersSettings settings = BuildHelpersSettings.from(section);

        assertFalse(settings.ironDoorToggle());
        assertTrue(settings.doubleSlabBreak());
        assertFalse(settings.glazedTerracottaRotate());
        assertFalse(settings.requireSneakForSlab());
        assertTrue(settings.requireSneakForRotate());
    }
}
