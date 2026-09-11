package com.halokaryamedia.lazybuilder.world.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Canonical in-memory owner for LazyBuilder-managed world metadata. */
public final class WorldRegistry {
    private final Map<WorldId, WorldRecord> worlds = new LinkedHashMap<>();

    public synchronized void register(WorldRecord world) {
        Objects.requireNonNull(world, "world");
        if (worlds.containsKey(world.id())) {
            throw new IllegalArgumentException("World id already registered: " + world.id());
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
}
