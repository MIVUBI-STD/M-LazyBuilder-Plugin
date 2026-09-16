package com.halokaryamedia.lazybuilder.world.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Export-only settings applied to the staged copy, never to the managed source world.
 *
 * <p>The legacy/default option preserves the historical direct native-export path.
 * The Map Export workspace uses {@link #workspaceDefaults()} so empty-chunk cleanup is
 * automatic without exposing converter internals as a builder-facing setting.</p>
 */
public record WorldExportOptions(
        WorldGameMode gameMode,
        WorldDifficulty difficulty,
        Integer spawnX,
        Integer spawnY,
        Integer spawnZ,
        Long timeOfDayTicks,
        WorldWeather weather,
        Map<String, String> gameRules,
        boolean discardEmptyChunks
) {
    private static final Pattern RULE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");

    /** Compatibility constructor for the first export-customization contract. */
    public WorldExportOptions(
            WorldGameMode gameMode,
            WorldDifficulty difficulty,
            Map<String, String> gameRules,
            boolean discardEmptyChunks
    ) {
        this(gameMode, difficulty, null, null, null, null, null, gameRules, discardEmptyChunks);
    }

    public WorldExportOptions {
        boolean anySpawn = spawnX != null || spawnY != null || spawnZ != null;
        boolean fullSpawn = spawnX != null && spawnY != null && spawnZ != null;
        if (anySpawn && !fullSpawn) {
            throw new IllegalArgumentException("Spawn override must include X, Y, and Z");
        }
        if (timeOfDayTicks != null && (timeOfDayTicks < 0 || timeOfDayTicks >= 24000)) {
            throw new IllegalArgumentException("Export time must be in range 0..23999");
        }

        Map<String, String> sourceRules = gameRules == null ? Map.of() : gameRules;
        Map<String, String> normalizedRules = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sourceRules.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "gameRule name").strip();
            String value = Objects.requireNonNull(entry.getValue(), "gameRule value").strip();
            if (!RULE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Game rule name is invalid: " + name);
            }
            if (value.isEmpty() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Game rule value is invalid: " + name);
            }
            normalizedRules.put(name, value);
        }
        gameRules = Map.copyOf(normalizedRules);
    }

    /** Historical behavior for existing callers: no forced converter pass. */
    public static WorldExportOptions legacyDefaults() {
        return new WorldExportOptions(null, null, null, null, null, null, null, Map.of(), false);
    }

    /** Builder-facing Export workspace defaults: cleanup is automatic. */
    public static WorldExportOptions workspaceDefaults() {
        return new WorldExportOptions(null, null, null, null, null, null, null, Map.of(), true);
    }

    public boolean hasSpawnOverride() {
        return spawnX != null;
    }

    public boolean hasWorldOverrides() {
        return gameMode != null
                || difficulty != null
                || hasSpawnOverride()
                || timeOfDayTicks != null
                || weather != null
                || !gameRules.isEmpty();
    }

    public boolean requiresConverterPass() {
        return discardEmptyChunks || hasWorldOverrides();
    }
}
