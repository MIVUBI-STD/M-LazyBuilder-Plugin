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
    private final WorldOperationCoordinator operations;
    private final BuildReadyPolicy buildReadyPolicy;

    public WorldSettingsService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldRuntimeGateway runtime,
            WorldOperationCoordinator operations,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.buildReadyPolicy = Objects.requireNonNull(buildReadyPolicy, "buildReadyPolicy");
    }

    public synchronized WorldSettingsSnapshot snapshot(WorldId worldId) {
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            WorldRecord world = requireLoadedDuringSettings(worldId);
            return new WorldSettingsSnapshot(
                    world,
                    WorldGameMode.valueOf(world.defaultGameMode()),
                    runtime.readSettings(world)
            );
        }
    }

    public synchronized WorldRecord setDefaultGameMode(WorldId worldId, WorldGameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            WorldRecord current = requireWorld(worldId);
            return persistMetadataChange(current, current.withDefaultGameMode(gameMode.name()));
        }
    }

    public synchronized void setDifficulty(WorldId worldId, WorldDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        withSettingsLease(worldId, world -> runtime.setDifficulty(world, difficulty));
    }

    public synchronized void setPvp(WorldId worldId, boolean enabled) {
        withSettingsLease(worldId, world -> runtime.setPvp(world, enabled));
    }

    public synchronized void setTime(WorldId worldId, long ticks) {
        if (ticks < 0 || ticks >= 24000) {
            throw new IllegalArgumentException("ticks must be in range 0..23999");
        }
        withSettingsLease(worldId, world -> runtime.setTime(world, ticks));
    }

    public synchronized void setWeather(WorldId worldId, WorldWeather weather) {
        Objects.requireNonNull(weather, "weather");
        withSettingsLease(worldId, world -> runtime.setWeather(world, weather));
    }

    public synchronized void setGameRule(WorldId worldId, String ruleName, String value) {
        Objects.requireNonNull(ruleName, "ruleName");
        Objects.requireNonNull(value, "value");
        if (ruleName.isBlank()) throw new IllegalArgumentException("ruleName must not be blank");
        withSettingsLease(worldId, world -> runtime.setGameRule(world, ruleName, value));
    }

    public synchronized void setSpawnToPlayer(UUID playerId, WorldId worldId) {
        Objects.requireNonNull(playerId, "playerId");
        withSettingsLease(worldId, world -> runtime.setSpawnToPlayer(playerId, world));
    }

    public synchronized WorldSettingsSnapshot setSpawning(
            WorldId worldId,
            WorldSpawnControl control,
            boolean enabled
    ) {
        Objects.requireNonNull(control, "control");
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            WorldRecord world = requireLoadedDuringSettings(worldId);
            runtime.setSpawning(world, control, enabled);
            return new WorldSettingsSnapshot(
                    world,
                    WorldGameMode.valueOf(world.defaultGameMode()),
                    runtime.readSettings(world)
            );
        }
    }

    public synchronized WorldSettingsSnapshot applyBatch(
            WorldId worldId,
            WorldGameMode defaultGameMode,
            Long timeOfDayTicks,
            WorldWeather weather,
            Boolean naturalSpawning,
            Boolean daylightCycle,
            Boolean weatherCycle
    ) {
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            WorldRecord world = requireLoadedDuringSettings(worldId);

            if (defaultGameMode != null) {
                world = persistMetadataChange(
                        world,
                        world.withDefaultGameMode(defaultGameMode.name()));
            }
            if (timeOfDayTicks != null) {
                if (timeOfDayTicks < 0L || timeOfDayTicks >= 24_000L) {
                    throw new IllegalArgumentException("ticks must be in range 0..23999");
                }
                runtime.setTime(world, timeOfDayTicks);
            }
            if (weather != null) {
                runtime.setWeather(world, weather);
            }
            if (naturalSpawning != null) {
                runtime.setSpawning(world, WorldSpawnControl.NATURAL, naturalSpawning);
            }
            if (daylightCycle != null) {
                runtime.setGameRule(world, "doDaylightCycle", daylightCycle.toString());
            }
            if (weatherCycle != null) {
                runtime.setGameRule(world, "doWeatherCycle", weatherCycle.toString());
            }

            return new WorldSettingsSnapshot(
                    world,
                    WorldGameMode.valueOf(world.defaultGameMode()),
                    runtime.readSettings(world)
            );
        }
    }

    public synchronized WorldSettingsSnapshot resetToBuildReady(WorldId worldId) {
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            WorldRecord current = requireLoadedDuringSettings(worldId);
            WorldRecord updated = current.withDefaultGameMode(buildReadyPolicy.defaultGameMode().name());

            updated = persistMetadataChange(current, updated);
            try {
                runtime.applyBuildReady(updated, buildReadyPolicy);
            } catch (RuntimeException runtimeFailure) {
                if (!updated.equals(current)) {
                    try {
                        persistMetadataChange(updated, current);
                    } catch (RuntimeException rollbackFailure) {
                        runtimeFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw new IllegalStateException(
                        "Failed to apply BUILD_READY runtime settings: " + current.folderName(),
                        runtimeFailure);
            }

            return new WorldSettingsSnapshot(
                    updated,
                    buildReadyPolicy.defaultGameMode(),
                    runtime.readSettings(updated)
            );
        }
    }

    private WorldRecord requireLoadedDuringSettings(WorldId worldId) {
        WorldRecord world = requireWorld(worldId);
        runtimeService.loadDuringOperation(worldId);
        return world;
    }

    private void withSettingsLease(
            WorldId worldId,
            java.util.function.Consumer<WorldRecord> mutation
    ) {
        Objects.requireNonNull(mutation, "mutation");
        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.SETTINGS)) {
            mutation.accept(requireLoadedDuringSettings(worldId));
        }
    }

    private WorldRecord requireWorld(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
    }

    private WorldRecord persistMetadataChange(WorldRecord previous, WorldRecord updated) {
        if (previous.equals(updated)) return previous;
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
