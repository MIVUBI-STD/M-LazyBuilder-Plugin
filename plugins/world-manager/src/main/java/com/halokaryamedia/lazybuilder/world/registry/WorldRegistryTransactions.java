package com.halokaryamedia.lazybuilder.world.registry;

import java.io.IOException;
import java.util.Objects;

/**
 * Atomic mutation boundary for the single durable WorldRegistry authority.
 *
 * <p>Every durable registry mutation is serialized on the persistence owner and
 * keeps the in-memory registry monitor through publication. A failed save restores
 * the previous in-memory state before the transaction releases either owner.</p>
 */
public final class WorldRegistryTransactions {
    private WorldRegistryTransactions() { }

    public static WorldRecord register(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRecord world
    ) throws IOException {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(world, "world");
        synchronized (persistence) {
            synchronized (registry) {
                registry.register(world);
                try {
                    persistence.save(registry.all());
                    return world;
                } catch (IOException | RuntimeException failure) {
                    registry.remove(world.id());
                    throw failure;
                }
            }
        }
    }

    public static WorldRecord updateMetadata(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRecord updated
    ) throws IOException {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(updated, "updated");
        synchronized (persistence) {
            synchronized (registry) {
                WorldRecord previous = registry.find(updated.id())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "World id is not registered: " + updated.id()));
                if (previous.equals(updated)) return previous;
                registry.updateMetadata(updated);
                try {
                    persistence.save(registry.all());
                    return updated;
                } catch (IOException | RuntimeException failure) {
                    registry.updateMetadata(previous);
                    throw failure;
                }
            }
        }
    }

    public static WorldRecord remove(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldId worldId
    ) throws IOException {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(worldId, "worldId");
        synchronized (persistence) {
            synchronized (registry) {
                WorldRecord previous = registry.find(worldId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "World id is not registered: " + worldId));
                registry.remove(worldId);
                try {
                    persistence.save(registry.all());
                    return previous;
                } catch (IOException | RuntimeException failure) {
                    registry.register(previous);
                    throw failure;
                }
            }
        }
    }
}
