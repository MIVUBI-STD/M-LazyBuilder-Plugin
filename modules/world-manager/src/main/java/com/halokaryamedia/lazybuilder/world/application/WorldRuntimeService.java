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

    public WorldRuntimeService(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
            WorldRuntimeGateway runtime
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.states = Objects.requireNonNull(states, "states");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public WorldRuntimeState state(WorldId id) {
        return states.get(id);
    }

    public WorldRecord load(WorldId id) {
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

    public WorldRecord unload(WorldId id) {
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

    private WorldRecord requireWorld(WorldId id) {
        return registry.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + id));
    }
}
