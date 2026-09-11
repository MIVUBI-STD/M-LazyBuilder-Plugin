package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Canonical server-authoritative World Settings use cases. */
public final class WorldSettingsService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeGateway runtime;
    private final BuildReadyPolicy buildReadyPolicy;

    public WorldSettingsService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldRuntimeGateway runtime,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.buildReadyPolicy = Objects.requireNonNull(buildReadyPolicy, "buildReadyPolicy");
    }

    public synchronized WorldSettingsSnapshot snapshot(WorldId worldId) {
        WorldRecord world = requireWorld(worldId);
        runtimeService.load(worldId);
        return new WorldSettingsSnapshot(
                world,
                WorldGameMode.valueOf(world.defaultGameMode()),
                runtime.readSettings(world)
        );
    }

    public synchronized WorldRecord setAutoLoad(WorldId worldId, boolean autoLoad) {
        WorldRecord current = requireWorld(worldId);
        return persistMetadataChange(current, current.withAutoLoad(autoLoad));
    }

    public synchronized WorldRecord setDefaultGameMode(WorldId worldId, WorldGameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        WorldRecord current = requireWorld(worldId);
        return persistMetadataChange(current, current.withDefaultGameMode(gameMode.name()));
    }

    public synchronized void setDifficulty(WorldId worldId, WorldDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        WorldRecord world = requireLoaded(worldId);
        runtime.setDifficulty(world, difficulty);
    }

    public synchronized void setPvp(WorldId worldId, boolean enabled) {
        WorldRecord world = requireLoaded(worldId);
        runtime.setPvp(world, enabled);
    }

    public synchronized void setTime(WorldId worldId, long ticks) {
        if (ticks < 0 || ticks >= 24000) {
            throw new IllegalArgumentException("ticks must be in range 0..23999");
        }
        WorldRecord world = requireLoaded(worldId);
        runtime.setTime(world, ticks);
    }

    public synchronized void setWeather(WorldId worldId, WorldWeather weather) {
        Objects.requireNonNull(weather, "weather");
        WorldRecord world = requireLoaded(worldId);
        runtime.setWeather(world, weather);
    }

    public synchronized void setGameRule(WorldId worldId, String ruleName, String value) {
        Objects.requireNonNull(ruleName, "ruleName");
        Objects.requireNonNull(value, "value");
        if (ruleName.isBlank()) {
            throw new IllegalArgumentException("ruleName must not be blank");
        }
        WorldRecord world = requireLoaded(worldId);
        runtime.setGameRule(world, ruleName, value);
    }

    public synchronized void setSpawnToPlayer(UUID playerId, WorldId worldId) {
        Objects.requireNonNull(playerId, "playerId");
        WorldRecord world = requireLoaded(worldId);
        runtime.setSpawnToPlayer(playerId, world);
    }

    public synchronized WorldSettingsSnapshot resetToBuildReady(WorldId worldId) {
        WorldRecord current = requireLoaded(worldId);
        runtime.applyBuildReady(current, buildReadyPolicy);
        WorldRecord updated = persistMetadataChange(
                current,
                current.withDefaultGameMode(buildReadyPolicy.defaultGameMode().name())
        );
        return new WorldSettingsSnapshot(
                updated,
                buildReadyPolicy.defaultGameMode(),
                runtime.readSettings(updated)
        );
    }

    private WorldRecord requireLoaded(WorldId worldId) {
        WorldRecord world = requireWorld(worldId);
        runtimeService.load(worldId);
        return world;
    }

    private WorldRecord requireWorld(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
    }

    private WorldRecord persistMetadataChange(WorldRecord previous, WorldRecord updated) {
        if (previous.equals(updated)) {
            return previous;
        }
        registry.updateMetadata(updated);
        try {
            persistence.save(registry.all());
            return updated;
        } catch (IOException | RuntimeException exception) {
            registry.updateMetadata(previous);
            try {
                persistence.save(registry.all());
            } catch (IOException | RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException("Failed to persist world settings metadata: " + previous.folderName(), exception);
        }
    }
}
