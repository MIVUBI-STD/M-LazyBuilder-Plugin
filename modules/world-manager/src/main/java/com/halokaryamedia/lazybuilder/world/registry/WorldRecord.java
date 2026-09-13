package com.halokaryamedia.lazybuilder.world.registry;

import java.util.Objects;

/**
 * Durable LazyBuilder metadata for one managed world.
 *
 * <p>The world folder is filesystem identity and is not a presentation name.
 * Runtime load state is intentionally not stored here; Paper is the runtime authority.</p>
 */
public record WorldRecord(
        WorldId id,
        String folderName,
        String displayName,
        WorldKind kind,
        WorldLifecycle lifecycle,
        String defaultGameMode
) {
    public static final String DEFAULT_GAME_MODE = "CREATIVE";

    public WorldRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lifecycle, "lifecycle");
        folderName = validateFolderName(folderName);
        displayName = validateDisplayName(displayName);
        defaultGameMode = validateGameMode(defaultGameMode);
    }

    public WorldRecord(
            WorldId id,
            String folderName,
            String displayName,
            WorldKind kind,
            WorldLifecycle lifecycle
    ) {
        this(id, folderName, displayName, kind, lifecycle, DEFAULT_GAME_MODE);
    }

    public WorldRecord withDisplayName(String newDisplayName) {
        return new WorldRecord(id, folderName, newDisplayName, kind, lifecycle, defaultGameMode);
    }

    public WorldRecord withLifecycle(WorldLifecycle newLifecycle) {
        return new WorldRecord(id, folderName, displayName, kind, newLifecycle, defaultGameMode);
    }

    public WorldRecord withDefaultGameMode(String newDefaultGameMode) {
        return new WorldRecord(id, folderName, displayName, kind, lifecycle, newDefaultGameMode);
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

    private static String validateGameMode(String value) {
        Objects.requireNonNull(value, "defaultGameMode");
        String normalized = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("SURVIVAL") && !normalized.equals("CREATIVE")
                && !normalized.equals("ADVENTURE") && !normalized.equals("SPECTATOR")) {
            throw new IllegalArgumentException("Unsupported default game mode: " + value);
        }
        return normalized;
    }
}
