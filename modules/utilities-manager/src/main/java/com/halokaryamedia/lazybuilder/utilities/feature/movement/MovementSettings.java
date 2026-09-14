package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/** Immutable configuration for the Utilities-Manager movement feature family. */
public record MovementSettings(
        boolean fly,
        boolean noclip,
        boolean nightVision
) {
    public static MovementSettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        ConfigurationSection abilities = section.getConfigurationSection("abilities");
        if (abilities == null) {
            return new MovementSettings(true, true, true);
        }
        boolean flyEnabled = abilities.contains("fly")
                ? abilities.getBoolean("fly", true)
                : abilities.getBoolean("advanced-fly", true);
        return new MovementSettings(
                flyEnabled,
                abilities.getBoolean("noclip", true),
                abilities.getBoolean("night-vision", true)
        );
    }

    /** Source-compatible accessor while existing runtime code migrates to the simpler config name. */
    public boolean advancedFly() {
        return fly;
    }
}
