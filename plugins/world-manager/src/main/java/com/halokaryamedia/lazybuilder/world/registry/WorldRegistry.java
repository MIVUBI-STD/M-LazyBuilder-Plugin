package com.halokaryamedia.lazybuilder.world.registry;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical in-memory owner for LazyBuilder-managed world metadata. */
public final class WorldRegistry {
    public static final int MAX_MANAGED_WORLDS = 4_096;

    private final Map<WorldId, WorldRecord> worlds = new LinkedHashMap<>();
    private final Set<String> reservedFolders = new HashSet<>();

    public synchronized void register(WorldRecord world) {
        Objects.requireNonNull(world, "world");
        if (worlds.containsKey(world.id())) {
            throw new IllegalArgumentException("World id already registered: " + world.id());
        }
        if (worlds.size() >= MAX_MANAGED_WORLDS) {
            throw new IllegalStateException(
                    "Managed world limit reached: " + MAX_MANAGED_WORLDS);
        }
        ensureFolderAvailable(world.folderName(), null);
        worlds.put(world.id(), world);
    }

    public synchronized Optional<WorldRecord> find(WorldId id) {
        return Optional.ofNullable(worlds.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<WorldRecord> findByFolderName(String folderName) {
        String target = folderKey(Objects.requireNonNull(folderName, "folderName"));
        return worlds.values().stream()
                .filter(world -> folderKey(world.folderName()).equals(target))
                .findFirst();
    }

    public synchronized List<WorldRecord> all() {
        return List.copyOf(worlds.values());
    }

    /**
     * Reserves one destination folder while Create/Clone/Import is publishing it.
     * This prevents two concurrent publication paths from both passing the initial
     * registry check before either has durably registered the destination.
     */
    public synchronized FolderReservation reserveFolder(String folderName) {
        Objects.requireNonNull(folderName, "folderName");
        if (folderName.isBlank()) {
            throw new IllegalArgumentException("folderName must not be blank");
        }
        String key = folderKey(folderName);
        ensureFolderAvailable(folderName, null);
        if ((long) worlds.size() + reservedFolders.size() >= MAX_MANAGED_WORLDS) {
            throw new IllegalStateException(
                    "Managed world limit reached: " + MAX_MANAGED_WORLDS);
        }
        if (!reservedFolders.add(key)) {
            throw new IllegalStateException("World folder is already being prepared: " + folderName);
        }
        return new FolderReservation(key);
    }

    /**
     * Replaces mutable metadata while preserving stable id and filesystem identity.
     */
    public synchronized WorldRecord updateMetadata(WorldRecord updated) {
        Objects.requireNonNull(updated, "updated");
        WorldRecord current = worlds.get(updated.id());
        if (current == null) {
            throw new IllegalArgumentException("World id is not registered: " + updated.id());
        }
        if (!current.folderName().equals(updated.folderName())) {
            throw new IllegalArgumentException("folderName cannot be changed through registry metadata updates");
        }
        worlds.put(updated.id(), updated);
        return updated;
    }

    public synchronized Optional<WorldRecord> remove(WorldId id) {
        return Optional.ofNullable(worlds.remove(Objects.requireNonNull(id, "id")));
    }

    public synchronized int size() {
        return worlds.size();
    }

    private void ensureFolderAvailable(String folderName, WorldId exceptId) {
        String target = folderKey(folderName);
        boolean exists = worlds.values().stream()
                .anyMatch(world -> !world.id().equals(exceptId) && folderKey(world.folderName()).equals(target));
        if (exists) {
            throw new IllegalArgumentException("World folder already registered: " + folderName);
        }
    }

    private static String folderKey(String folderName) {
        return folderName.toLowerCase(Locale.ROOT);
    }

    public final class FolderReservation implements AutoCloseable {
        private final String key;
        private boolean closed;

        private FolderReservation(String key) {
            this.key = key;
        }

        @Override
        public void close() {
            synchronized (WorldRegistry.this) {
                if (closed) return;
                if (!reservedFolders.remove(key)) {
                    throw new IllegalStateException("World folder reservation ownership changed unexpectedly: " + key);
                }
                closed = true;
            }
        }
    }
}
