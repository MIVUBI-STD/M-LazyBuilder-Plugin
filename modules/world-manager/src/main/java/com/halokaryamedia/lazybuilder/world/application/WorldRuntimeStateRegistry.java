package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical in-memory owner for ephemeral runtime load state. */
public final class WorldRuntimeStateRegistry {
    private final Map<WorldId, WorldRuntimeState> states = new LinkedHashMap<>();

    public synchronized void initialize(WorldId id, WorldRuntimeState state) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        if (states.putIfAbsent(id, state) != null) {
            throw new IllegalArgumentException("Runtime state already initialized for world: " + id);
        }
    }

    public synchronized WorldRuntimeState get(WorldId id) {
        WorldRuntimeState state = states.get(Objects.requireNonNull(id, "id"));
        if (state == null) {
            throw new IllegalArgumentException("Runtime state is not initialized for world: " + id);
        }
        return state;
    }

    public synchronized void transition(WorldId id, WorldRuntimeState expected, WorldRuntimeState next) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        WorldRuntimeState current = get(id);
        if (current != expected) {
            throw new IllegalStateException("Expected " + expected + " but found " + current + " for world " + id);
        }
        states.put(id, next);
    }

    public synchronized void remove(WorldId id) {
        states.remove(Objects.requireNonNull(id, "id"));
    }
}
