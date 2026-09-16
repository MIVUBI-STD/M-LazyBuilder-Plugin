package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runtime boundary used by World Manager application services. */
public interface WorldRuntimeGateway {
    void createNewWorld(WorldRecord world, BuildReadyPolicy policy);

    void rollbackCreatedWorld(WorldRecord world);

    boolean isLoaded(WorldRecord world);

    /** Runtime-authoritative player presence for the complete managed world family. */
    default boolean hasPlayers(WorldRecord world) {
        return false;
    }

    void loadWorld(WorldRecord world);

    void unloadWorld(WorldRecord world);

    /**
     * Flushes a loaded world's current state to disk and pauses periodic autosave while
     * a read-only filesystem snapshot is captured. Legacy runtimes may expose one save
     * state; Paper overrides the family-aware methods below for all loaded dimensions.
     */
    default boolean beginLiveSnapshot(WorldRecord world) {
        throw new UnsupportedOperationException("Live world snapshots are not supported by this runtime");
    }

    /** Restores the legacy runtime state captured by {@link #beginLiveSnapshot(WorldRecord)}. */
    default void endLiveSnapshot(WorldRecord world, boolean previousAutoSave) {
        throw new UnsupportedOperationException("Live world snapshots are not supported by this runtime");
    }

    /**
     * Begins one consistent snapshot window for every loaded runtime world that belongs
     * to the managed world. The returned token is request-owned and must be restored.
     */
    default LiveSnapshotState beginManagedLiveSnapshot(WorldRecord world) {
        return LiveSnapshotState.single(beginLiveSnapshot(world));
    }

    /** Restores the exact autosave state captured for a managed-world snapshot window. */
    default void endManagedLiveSnapshot(WorldRecord world, LiveSnapshotState state) {
        endLiveSnapshot(world, Objects.requireNonNull(state, "state").legacyPreviousAutoSave());
    }

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
        throw new UnsupportedOperationException("Builder Defaults reset is not supported by this runtime");
    }

    /** Immutable request token; keys are runtime-owned world identifiers. */
    record LiveSnapshotState(Map<String, Boolean> autoSaveByRuntimeWorld) {
        private static final String LEGACY_WORLD = "__primary__";

        public LiveSnapshotState {
            Map<String, Boolean> source = Objects.requireNonNull(autoSaveByRuntimeWorld, "autoSaveByRuntimeWorld");
            if (source.isEmpty()) throw new IllegalArgumentException("Snapshot state must not be empty");
            Map<String, Boolean> copy = new LinkedHashMap<>();
            source.forEach((name, enabled) -> {
                String key = Objects.requireNonNull(name, "runtime world").strip();
                if (key.isEmpty()) throw new IllegalArgumentException("Runtime world identifier must not be blank");
                copy.put(key, Objects.requireNonNull(enabled, "autosave state"));
            });
            autoSaveByRuntimeWorld = Map.copyOf(copy);
        }

        public static LiveSnapshotState single(boolean previousAutoSave) {
            return new LiveSnapshotState(Map.of(LEGACY_WORLD, previousAutoSave));
        }

        boolean legacyPreviousAutoSave() {
            if (autoSaveByRuntimeWorld.size() != 1 || !autoSaveByRuntimeWorld.containsKey(LEGACY_WORLD)) {
                throw new IllegalStateException("Family snapshot state requires a family-aware runtime");
            }
            return autoSaveByRuntimeWorld.get(LEGACY_WORLD);
        }
    }
}
