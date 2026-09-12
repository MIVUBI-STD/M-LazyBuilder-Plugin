package com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/** Immutable configuration for the Build Helpers feature family. */
public record BuildHelpersSettings(
        boolean ironDoorToggle,
        boolean doubleSlabBreak,
        boolean glazedTerracottaRotate,
        boolean requireSneakForSlab,
        boolean requireSneakForRotate
) {
    public static BuildHelpersSettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        return new BuildHelpersSettings(
                section.getBoolean("helpers.iron-door-toggle", true),
                section.getBoolean("helpers.double-slab-break", true),
                section.getBoolean("helpers.glazed-terracotta-rotate", true),
                section.getBoolean("interaction.require-sneak-for-slab", true),
                section.getBoolean("interaction.require-sneak-for-rotate", true)
        );
    }
}
