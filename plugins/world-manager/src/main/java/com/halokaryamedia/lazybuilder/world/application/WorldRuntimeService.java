package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;
import java.util.function.Predicate;

/** Canonical runtime load boundary. Loaded/unloaded is derived from Paper, not stored as world state. */
public final class WorldRuntimeService {
    private final WorldRegistry registry;
    private final WorldRuntimeGateway runtime;
    private final Predicate<WorldRecord> additionalPlayerPresence;
    private WorldOperationCoordinator operations;

    public WorldRuntimeService(WorldRegistry registry, WorldRuntimeGateway runtime) {
        this(registry, runtime, null, ignored -> false);
    }

    public WorldRuntimeService(
            WorldRegistry registry,
            WorldRuntimeGateway runtime,
            WorldOperationCoordinator operations
    ) {
        this(registry, runtime, operations, ignored -> false);
    }

    /**
     * The predicate is retained as a test/integration extension point. Production Paper
     * player presence comes from the runtime gateway so dimension siblings share one owner.
     */
    public WorldRuntimeService(
            WorldRegistry registry,
            WorldRuntimeGateway runtime,
            WorldOperationCoordinator operations,
            Predicate<WorldRecord> additionalPlayerPresence
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = operations;
        this.additionalPlayerPresence = Objects.requireNonNull(additionalPlayerPresence, "additionalPlayerPresence");
    }

    synchronized void attachOperations(WorldOperationCoordinator coordinator) {
        Objects.requireNonNull(coordinator, "coordinator");
        if (operations == null) {
            operations = coordinator;
            return;
        }
        if (operations != coordinator) {
            throw new IllegalStateException("WorldRuntimeService already uses a different operation coordinator");
        }
    }

    public boolean isLoaded(WorldId id) {
        return runtime.isLoaded(requireWorld(id));
    }

    /** Player presence is ephemeral runtime truth and never becomes world metadata. */
    public boolean hasPlayers(WorldId id) {
        WorldRecord world = requireWorld(id);
        return runtime.hasPlayers(world) || additionalPlayerPresence.test(world);
    }

    public WorldRecord load(WorldId id) {
        ensureNoExternalOperation(id);
        return loadInternal(id);
    }

    public WorldRecord unload(WorldId id) {
        ensureNoExternalOperation(id);
        return unloadInternal(id);
    }

    WorldRecord loadDuringOperation(WorldId id) {
        return loadInternal(id);
    }

    WorldRecord unloadDuringOperation(WorldId id) {
        return unloadInternal(id);
    }

    /** Begins a snapshot window without moving players or unloading the managed world family. */
    WorldRuntimeGateway.LiveSnapshotState beginLiveSnapshotDuringOperation(WorldId id) {
        WorldRecord world = requireWorld(id);
        if (world.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before use");
        }
        if (!runtime.isLoaded(world)) {
            throw new IllegalStateException("Live snapshot requires a loaded world: " + world.displayName());
        }
        return runtime.beginManagedLiveSnapshot(world);
    }

    /** Ends a snapshot window and restores the exact previous runtime save policy. */
    void endLiveSnapshotDuringOperation(WorldId id, WorldRuntimeGateway.LiveSnapshotState snapshotState) {
        WorldRecord world = requireWorld(id);
        if (!runtime.isLoaded(world)) {
            throw new IllegalStateException("Live snapshot source is no longer loaded: " + world.displayName());
        }
        runtime.endManagedLiveSnapshot(world, Objects.requireNonNull(snapshotState, "snapshotState"));
    }

    private WorldRecord loadInternal(WorldId id) {
        WorldRecord world = requireWorld(id);
        if (world.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before use");
        }
        if (!runtime.isLoaded(world)) runtime.loadWorld(world);
        return world;
    }

    private WorldRecord unloadInternal(WorldId id) {
        WorldRecord world = requireWorld(id);
        if (runtime.hasPlayers(world) || additionalPlayerPresence.test(world)) {
            throw new IllegalStateException("Cannot unload " + world.displayName()
                    + " while builders are inside the world");
        }
        if (runtime.isLoaded(world)) runtime.unloadWorld(world);
        return world;
    }

    private void ensureNoExternalOperation(WorldId id) {
        Objects.requireNonNull(id, "id");
        WorldOperationCoordinator coordinator;
        synchronized (this) {
            coordinator = operations;
        }
        if (coordinator == null) return;
        WorldOperationType active = coordinator.activeOperation(id);
        if (active != null) {
            throw new IllegalStateException("World is busy with " + active + ": " + id);
        }
    }

    private WorldRecord requireWorld(WorldId id) {
        return registry.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + id));
    }
}
