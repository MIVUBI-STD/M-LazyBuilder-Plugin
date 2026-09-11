package com.halokaryamedia.lazybuilder.world.registry;

import java.util.Objects;

/**
 * Durable LazyBuilder metadata for one managed world.
 *
 * <p>The world folder is filesystem identity and is not a presentation name.
 * Runtime load state is intentionally not stored here.</p>
 */
public record WorldRecord(
        WorldId id,
        String folderName,
        String displayName,
        WorldKind kind,
        WorldLifecycle lifecycle,
        boolean autoLoad
) {
    public WorldRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lifecycle, "lifecycle");
        folderName = validateFolderName(folderName);
        displayName = validateDisplayName(displayName);
    }

    public WorldRecord withDisplayName(String newDisplayName) {
        return new WorldRecord(id, folderName, newDisplayName, kind, lifecycle, autoLoad);
    }

    public WorldRecord withLifecycle(WorldLifecycle newLifecycle) {
        return new WorldRecord(id, folderName, displayName, kind, newLifecycle, autoLoad);
    }

    public WorldRecord withAutoLoad(boolean newAutoLoad) {
        return new WorldRecord(id, folderName, displayName, kind, lifecycle, newAutoLoad);
    }

    private static String validateFolderName(String value) {
        Objects.requireNonNull(value, "folderName");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("folderName must be non-blank and have no leading/trailing whitespace");
        }
        if (value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("folderName must be a single safe world-directory name");
        }
        return value;
    }

    private static String validateDisplayName(String value) {
        Objects.requireNonNull(value, "displayName");
        if (value.isBlank()) {
            throw new IllegalArgumentException("displayName must be non-blank");
        }
        return value;
    }
}
