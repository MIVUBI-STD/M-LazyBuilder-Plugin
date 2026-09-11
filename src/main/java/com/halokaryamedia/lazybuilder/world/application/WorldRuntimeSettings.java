package com.halokaryamedia.lazybuilder.world.application;

import java.util.List;
import java.util.Objects;

/** Runtime-backed settings snapshot for one managed world. */
public record WorldRuntimeSettings(
        WorldDifficulty difficulty,
        boolean pvpEnabled,
        WorldWeather weather,
        long timeOfDayTicks,
        WorldSpawnSetting spawn,
        WorldSpawningSettings spawning,
        List<GameRuleSetting> gamerules
) {
    public WorldRuntimeSettings {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(spawning, "spawning");
        gamerules = List.copyOf(Objects.requireNonNull(gamerules, "gamerules"));
    }
}
