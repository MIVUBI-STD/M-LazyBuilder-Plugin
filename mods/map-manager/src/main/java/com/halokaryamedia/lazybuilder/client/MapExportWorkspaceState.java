package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transient presentation state for the single-screen Map Export workspace.
 * Server/world truth remains in {@link ClientWorldController}; this object only
 * tracks builder choices and emits export-only overrides.
 */
final class MapExportWorkspaceState {
    enum Scope { FULL_WORLD, CUSTOM_AREA }

    private static final String BUILDER_GAME_MODE = "CREATIVE";
    private static final String BUILDER_DIFFICULTY = "NORMAL";
    private static final WorldTransferPreferences VERSION_PREFERENCES = new WorldTransferPreferences();

    private UUID worldId;
    private WorldControlWireProtocol.SettingsSnapshot source;
    private Scope scope = Scope.FULL_WORLD;
    private String artifactName = "world";
    private String format = "JAVA_1_21_4";
    private Integer spawnX;
    private Integer spawnY;
    private Integer spawnZ;
    private boolean worldSettingsExpanded;

    void initialize(
            UUID worldId,
            String displayName,
            WorldControlWireProtocol.SettingsSnapshot source,
            List<String> formats
    ) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(source, "source");
        boolean differentWorld = this.worldId == null || !this.worldId.equals(worldId);
        this.worldId = worldId;
        this.source = source;
        if (!differentWorld) return;

        artifactName = sanitizeWorldName(displayName);
        List<String> ordered = VERSION_PREFERENCES.preferredFirst(formats);
        format = ordered.isEmpty() ? "JAVA_1_21_4" : ordered.get(0);
        spawnX = round(source.spawnX());
        spawnY = round(source.spawnY());
        spawnZ = round(source.spawnZ());
        worldSettingsExpanded = false;
    }

    boolean initializedFor(UUID id) {
        return id != null && id.equals(worldId) && source != null;
    }

    Scope scope() { return scope; }
    void scope(Scope value) { scope = Objects.requireNonNull(value, "scope"); }
    String artifactName() { return artifactName; }
    void artifactName(String value) { artifactName = Objects.requireNonNullElse(value, ""); }
    String format() { return format; }
    boolean formatPreferred() { return VERSION_PREFERENCES.isPreferredFormat(format); }
    int spawnX() { return spawnX == null ? 0 : spawnX; }
    int spawnY() { return spawnY == null ? 0 : spawnY; }
    int spawnZ() { return spawnZ == null ? 0 : spawnZ; }
    boolean worldSettingsExpanded() { return worldSettingsExpanded; }
    void toggleWorldSettings() { worldSettingsExpanded = !worldSettingsExpanded; }

    void cycleFormat(List<String> formats) {
        List<String> ordered = VERSION_PREFERENCES.preferredFirst(formats);
        if (ordered.isEmpty()) return;
        format = next(ordered.toArray(String[]::new), format);
        VERSION_PREFERENCES.setExportFormat(format);
    }

    void useSpawn(int x, int y, int z) {
        spawnX = x;
        spawnY = y;
        spawnZ = z;
    }

    ExportSettingsWire.Settings toWire() {
        WorldControlWireProtocol.SettingsSnapshot base = Objects.requireNonNull(source, "source settings");
        VERSION_PREFERENCES.setExportFormat(format);

        Integer outSpawnX = null;
        Integer outSpawnY = null;
        Integer outSpawnZ = null;
        if (spawnX != round(base.spawnX()) || spawnY != round(base.spawnY()) || spawnZ != round(base.spawnZ())) {
            outSpawnX = spawnX;
            outSpawnY = spawnY;
            outSpawnZ = spawnZ;
        }

        return new ExportSettingsWire.Settings(
                same(BUILDER_GAME_MODE, base.defaultGameMode()) ? "" : BUILDER_GAME_MODE,
                same(BUILDER_DIFFICULTY, base.difficulty()) ? "" : BUILDER_DIFFICULTY,
                outSpawnX, outSpawnY, outSpawnZ,
                null,
                "",
                Map.of(),
                true
        );
    }

    static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean same(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").strip().toUpperCase(Locale.ROOT);
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static int round(double value) { return (int) Math.round(value); }

    private static String sanitizeWorldName(String displayName) {
        String base = Objects.requireNonNullElse(displayName, "world").strip()
                .replaceAll("[^A-Za-z0-9._ -]+", "")
                .replace(' ', '-');
        while (base.contains("--")) base = base.replace("--", "-");
        base = base.replaceAll("^[. -]+|[. -]+$", "");
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = "world";
        return base;
    }
}
