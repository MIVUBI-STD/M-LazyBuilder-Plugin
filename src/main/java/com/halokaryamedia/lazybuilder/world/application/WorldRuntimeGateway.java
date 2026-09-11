package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.UUID;

/**
 * Runtime boundary used by World Manager application services.
 *
 * <p>Paper/Bukkit details stay behind the implementation of this interface so
 * lifecycle policy and registry behavior remain independently testable.</p>
 */
public interface WorldRuntimeGateway {
    void createNewWorld(WorldRecord world, BuildReadyPolicy policy);

    void rollbackCreatedWorld(WorldRecord world);

    boolean isLoaded(WorldRecord world);

    void loadWorld(WorldRecord world);

    void unloadWorld(WorldRecord world);

    void teleportPlayerToSpawn(UUID playerId, WorldRecord world);

    default void teleportPlayerToSpawn(UUID playerId, WorldRecord world, WorldGameMode gameMode) {
        teleportPlayerToSpawn(playerId, world);
    }

    default WorldRuntimeSettings readSettings(WorldRecord world) {
        throw new UnsupportedOperationException("World settings are not supported by this runtime");
    }

    default void setDifficulty(WorldRecord world, WorldDifficulty difficulty) {
        throw new UnsupportedOperationException("Difficulty updates are not supported by this runtime");
    }

    default void setPvp(WorldRecord world, boolean enabled) {
        throw new UnsupportedOperationException("PVP updates are not supported by this runtime");
    }

    default void setTime(WorldRecord world, long ticks) {
        throw new UnsupportedOperationException("Time updates are not supported by this runtime");
    }

    default void setWeather(WorldRecord world, WorldWeather weather) {
        throw new UnsupportedOperationException("Weather updates are not supported by this runtime");
    }

    default void setGameRule(WorldRecord world, String ruleName, String value) {
        throw new UnsupportedOperationException("Gamerule updates are not supported by this runtime");
    }

    default void setSpawnToPlayer(UUID playerId, WorldRecord world) {
        throw new UnsupportedOperationException("Spawn updates are not supported by this runtime");
    }

    default void setSpawning(WorldRecord world, WorldSpawnControl control, boolean enabled) {
        throw new UnsupportedOperationException("Spawning updates are not supported by this runtime");
    }

    default void applyBuildReady(WorldRecord world, BuildReadyPolicy policy) {
        throw new UnsupportedOperationException("Build Ready reset is not supported by this runtime");
    }
}
