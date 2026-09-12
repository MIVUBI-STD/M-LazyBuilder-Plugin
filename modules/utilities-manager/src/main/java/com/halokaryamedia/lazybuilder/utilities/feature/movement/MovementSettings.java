package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/** Immutable configuration for the Utilities-Manager movement feature family. */
public record MovementSettings(
        boolean advancedFly,
        boolean noclip,
        boolean nightVision
) {
    public static MovementSettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        ConfigurationSection abilities = section.getConfigurationSection("abilities");
        if (abilities == null) {
            return new MovementSettings(true, true, true);
        }
        return new MovementSettings(
                abilities.getBoolean("advanced-fly", true),
                abilities.getBoolean("noclip", true),
                abilities.getBoolean("night-vision", true)
        );
    }
}
