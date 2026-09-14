package com.halokaryamedia.lazybuilder.world.application;

import java.util.Objects;

/**
 * Canonical initial policy applied once to worlds created by LazyBuilder.
 *
 * <p>This is an initialization profile, not a background enforcer. Later user
 * changes made through World Settings are authoritative until an explicit
 * "Reset to Build Ready" action reapplies this profile.</p>
 */
public record BuildReadyPolicy(
        boolean structuresEnabled,
        boolean naturalMobSpawning,
        WorldGameMode defaultGameMode,
        WorldDifficulty difficulty,
        boolean pvpEnabled,
        WorldWeather weather,
        boolean weatherCycle,
        boolean daylightCycle,
        long timeOfDayTicks,
        boolean fireTick,
        boolean mobGriefing,
        int randomTickSpeed,
        boolean patrolSpawning,
        boolean wanderingTraderSpawning,
        boolean insomniaEnabled,
        boolean wardenSpawning,
        boolean raidsEnabled,
        boolean spawnChunksPersistent
) {
    public static final long DAY_TIME_TICKS = 6000L;

    public BuildReadyPolicy {
        Objects.requireNonNull(defaultGameMode, "defaultGameMode");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(weather, "weather");
        if (timeOfDayTicks < 0 || timeOfDayTicks >= 24000) {
            throw new IllegalArgumentException("timeOfDayTicks must be in range 0..23999");
        }
        if (randomTickSpeed < 0) {
            throw new IllegalArgumentException("randomTickSpeed must not be negative");
        }
    }

    public static BuildReadyPolicy defaults() {
        return new BuildReadyPolicy(
                false,
                false,
                WorldGameMode.CREATIVE,
                WorldDifficulty.NORMAL,
                false,
                WorldWeather.CLEAR,
                false,
                false,
                DAY_TIME_TICKS,
                false,
                false,
                0,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
