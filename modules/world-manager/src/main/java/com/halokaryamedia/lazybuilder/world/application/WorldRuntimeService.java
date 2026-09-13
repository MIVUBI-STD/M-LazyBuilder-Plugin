package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;

/** Canonical load/unload use case for managed worlds. */
public final class WorldRuntimeService {
    private final WorldRegistry registry;
    private final WorldRuntimeStateRegistry states;
    private final WorldRuntimeGateway runtime;
    private final WorldOperationCoordinator operations;

    public WorldRuntimeService(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
            WorldRuntimeGateway runtime
    ) {
        this(registry, states, runtime, null);
    }

    public WorldRuntimeService(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
            WorldRuntimeGateway runtime,
            WorldOperationCoordinator operations
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.states = Objects.requireNonNull(states, "states");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = operations;
    }

    public WorldRuntimeState state(WorldId id) {
        return states.get(id);
    }

    public WorldRecord load(WorldId id) {
        ensureNoExternalOperation(id);
        return loadInternal(id);
    }

    public WorldRecord unload(WorldId id) {
        ensureNoExternalOperation(id);
        return unloadInternal(id);
    }

    /**
     * Internal lifecycle path for a service that already owns this world's operation lease.
     * Package-private on purpose so transports/UI cannot bypass operation coordination.
     */
    WorldRecord loadDuringOperation(WorldId id) {
        return loadInternal(id);
    }

    /** Internal counterpart to {@link #loadDuringOperation(WorldId)}. */
    WorldRecord unloadDuringOperation(WorldId id) {
        return unloadInternal(id);
    }

    private WorldRecord loadInternal(WorldId id) {
        WorldRecord world = requireWorld(id);
        if (world.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before loading");
        }

        WorldRuntimeState current = states.get(id);
        if (current == WorldRuntimeState.LOADED) {
            return world;
        }
        if (current != WorldRuntimeState.UNLOADED) {
            throw new IllegalStateException("World is busy: " + current);
        }

        states.transition(id, WorldRuntimeState.UNLOADED, WorldRuntimeState.LOADING);
        try {
            runtime.loadWorld(world);
            states.transition(id, WorldRuntimeState.LOADING, WorldRuntimeState.LOADED);
            return world;
        } catch (RuntimeException exception) {
            states.transition(id, WorldRuntimeState.LOADING, WorldRuntimeState.UNLOADED);
            throw exception;
        }
    }

    private WorldRecord unloadInternal(WorldId id) {
        WorldRecord world = requireWorld(id);
        WorldRuntimeState current = states.get(id);
        if (current == WorldRuntimeState.UNLOADED) {
            return world;
        }
        if (current != WorldRuntimeState.LOADED) {
            throw new IllegalStateException("World is busy: " + current);
        }

        states.transition(id, WorldRuntimeState.LOADED, WorldRuntimeState.UNLOADING);
        try {
            runtime.unloadWorld(world);
            states.transition(id, WorldRuntimeState.UNLOADING, WorldRuntimeState.UNLOADED);
            return world;
        } catch (RuntimeException exception) {
            states.transition(id, WorldRuntimeState.UNLOADING, WorldRuntimeState.LOADED);
            throw exception;
        }
    }

    private void ensureNoExternalOperation(WorldId id) {
        Objects.requireNonNull(id, "id");
        if (operations == null) {
            return;
        }
        WorldOperationType active = operations.activeOperation(id);
        if (active != null) {
            throw new IllegalStateException("World is busy with " + active + ": " + id);
        }
    }

    private WorldRecord requireWorld(WorldId id) {
        return registry.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + id));
    }
}
