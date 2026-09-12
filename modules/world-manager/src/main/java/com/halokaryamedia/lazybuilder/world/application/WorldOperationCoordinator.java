package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical in-memory owner for mutually exclusive world operations.
 *
 * <p>This is request-bound coordination only. It owns no scheduler, polling loop,
 * worker thread, or persistent queue.</p>
 */
public final class WorldOperationCoordinator {
    private final Map<WorldId, WorldOperationType> active = new LinkedHashMap<>();

    public synchronized Lease acquire(WorldId worldId, WorldOperationType type) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(type, "type");
        WorldOperationType current = active.putIfAbsent(worldId, type);
        if (current != null) {
            throw new IllegalStateException("World already has active operation " + current + ": " + worldId);
        }
        return new Lease(worldId, type);
    }

    public synchronized boolean isBusy(WorldId worldId) {
        return active.containsKey(Objects.requireNonNull(worldId, "worldId"));
    }

    public synchronized WorldOperationType activeOperation(WorldId worldId) {
        return active.get(Objects.requireNonNull(worldId, "worldId"));
    }

    public final class Lease implements AutoCloseable {
        private final WorldId worldId;
        private final WorldOperationType type;
        private boolean closed;

        private Lease(WorldId worldId, WorldOperationType type) {
            this.worldId = worldId;
            this.type = type;
        }

        @Override
        public void close() {
            synchronized (WorldOperationCoordinator.this) {
                if (closed) {
                    return;
                }
                WorldOperationType current = active.get(worldId);
                if (current != type) {
                    throw new IllegalStateException("World operation ownership changed unexpectedly for " + worldId);
                }
                active.remove(worldId);
                closed = true;
            }
        }
    }
}
