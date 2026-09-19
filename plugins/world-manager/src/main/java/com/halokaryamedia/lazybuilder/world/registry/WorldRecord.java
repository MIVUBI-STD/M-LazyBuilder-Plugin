package com.halokaryamedia.lazybuilder.world.registry;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
    private static final int MAX_FOLDER_NAME_CHARS = 255;
    private static final int MAX_DISPLAY_NAME_CHARS = 256;
    private static final Set<String> WINDOWS_RESERVED_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

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
        if (value.length() > MAX_FOLDER_NAME_CHARS) {
            throw new IllegalArgumentException(
                    "folderName exceeds the " + MAX_FOLDER_NAME_CHARS + " character portability limit");
        }
        if (value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("folderName must be a single safe world-directory name");
        }
        if (value.endsWith(".") || containsWindowsForbiddenCharacter(value) || isWindowsReservedDeviceName(value)) {
            throw new IllegalArgumentException("folderName must be portable across supported filesystems");
        }
        return value;
    }

    private static boolean containsWindowsForbiddenCharacter(String value) {
        return value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || value.indexOf(':') >= 0
                || value.indexOf('"') >= 0 || value.indexOf('|') >= 0 || value.indexOf('?') >= 0
                || value.indexOf('*') >= 0;
    }

    private static boolean isWindowsReservedDeviceName(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String stem = dot >= 0 ? upper.substring(0, dot) : upper;
        return WINDOWS_RESERVED_DEVICE_NAMES.contains(stem);
    }

    private static String validateDisplayName(String value) {
        Objects.requireNonNull(value, "displayName");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "displayName must be non-blank and have no leading/trailing whitespace");
        }
        if (value.length() > MAX_DISPLAY_NAME_CHARS) {
            throw new IllegalArgumentException(
                    "displayName exceeds the " + MAX_DISPLAY_NAME_CHARS + " character limit");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("displayName must not contain control characters");
        }
        return value;
    }

    private static String validateGameMode(String value) {
        Objects.requireNonNull(value, "defaultGameMode");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.equals("SURVIVAL") && !normalized.equals("CREATIVE")
                && !normalized.equals("ADVENTURE") && !normalized.equals("SPECTATOR")) {
            throw new IllegalArgumentException("Unsupported default game mode: " + value);
        }
        return normalized;
    }
}
