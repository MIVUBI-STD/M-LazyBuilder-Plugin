package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable behavior switches and world scope owned by World Safety. */
public record WorldSafetySettings(
        boolean explosions,
        boolean leavesDecay,
        boolean farmlandTrample,
        boolean dragonEggTeleport,
        String scopeMode,
        Set<String> includeWorlds,
        Set<String> excludeWorlds
) {
    public static WorldSafetySettings from(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        return new WorldSafetySettings(
                section.getBoolean("protections.explosions", true),
                section.getBoolean("protections.leaves-decay", true),
                section.getBoolean("protections.farmland-trample", true),
                section.getBoolean("protections.dragon-egg-teleport", true),
                Objects.requireNonNullElse(section.getString("scope.mode"), "all").trim().toLowerCase(Locale.ROOT),
                normalize(section.getStringList("scope.include-worlds")),
                normalize(section.getStringList("scope.exclude-worlds"))
        );
    }

    public boolean appliesTo(String worldName) {
        String normalized = normalizeName(worldName);
        if (excludeWorlds.contains(normalized)) return false;
        if (scopeMode.equals("include")) return includeWorlds.contains(normalized);
        return true;
    }

    private static Set<String> normalize(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(WorldSafetySettings::normalizeName)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
