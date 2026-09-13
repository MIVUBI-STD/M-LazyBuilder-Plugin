package com.halokaryamedia.lazybuilder.utilities.feature.spectator;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/** Immutable configuration for the Spectator helper family. */
public record SpectatorSettings(boolean playerTargeting) {
    public static SpectatorSettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        return new SpectatorSettings(section.getBoolean("helpers.player-targeting", true));
    }
}
