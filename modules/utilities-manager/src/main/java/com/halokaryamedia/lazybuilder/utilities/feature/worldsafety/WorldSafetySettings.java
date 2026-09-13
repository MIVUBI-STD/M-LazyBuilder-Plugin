package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/** Immutable behavior switches owned by the World Safety feature family. */
public record WorldSafetySettings(
        boolean explosions,
        boolean leavesDecay,
        boolean farmlandTrample,
        boolean dragonEggTeleport
) {
    public static WorldSafetySettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        return new WorldSafetySettings(
                section.getBoolean("protections.explosions", true),
                section.getBoolean("protections.leaves-decay", true),
                section.getBoolean("protections.farmland-trample", true),
                section.getBoolean("protections.dragon-egg-teleport", true)
        );
    }
}
