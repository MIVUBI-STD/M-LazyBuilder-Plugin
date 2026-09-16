package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;

import java.util.LinkedHashMap;
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

    static final String[] GAME_MODES = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    static final String[] DIFFICULTIES = {"PEACEFUL", "EASY", "NORMAL", "HARD"};
    static final String[] WEATHERS = {"CLEAR", "RAIN", "THUNDER"};
    static final String[] COMMON_RULES = {
            "keepInventory",
            "mobGriefing",
            "doMobSpawning",
            "doDaylightCycle",
            "doWeatherCycle",
            "doFireTick",
            "naturalRegeneration"
    };

    private UUID worldId;
    private WorldControlWireProtocol.SettingsSnapshot source;
    private Scope scope = Scope.FULL_WORLD;
    private String artifactName = "world-export";
    private String format = "JAVA_1_21_4";
    private String gameMode = "SURVIVAL";
    private String difficulty = "NORMAL";
    private Integer spawnX;
    private Integer spawnY;
    private Integer spawnZ;
    private Long timeOfDayTicks;
    private String weather = "CLEAR";
    private final Map<String, String> sourceGameRules = new LinkedHashMap<>();
    private final Map<String, String> gameRules = new LinkedHashMap<>();
    private boolean worldSettingsExpanded;
    private boolean gameRulesExpanded;

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

        artifactName = sanitizeArtifactName(displayName);
        format = formats == null || formats.isEmpty() ? "JAVA_1_21_4" : formats.get(0);
        gameMode = source.defaultGameMode();
        difficulty = source.difficulty();
        spawnX = round(source.spawnX());
        spawnY = round(source.spawnY());
        spawnZ = round(source.spawnZ());
        timeOfDayTicks = source.timeOfDayTicks();
        weather = source.weather();
        sourceGameRules.clear();
        gameRules.clear();
        for (WorldControlWireProtocol.GameRuleValue rule : source.gameRules()) {
            sourceGameRules.put(rule.name(), rule.value());
            gameRules.put(rule.name(), rule.value());
        }
        worldSettingsExpanded = false;
        gameRulesExpanded = false;
    }

    boolean initializedFor(UUID id) {
        return id != null && id.equals(worldId) && source != null;
    }

    Scope scope() { return scope; }
    void scope(Scope value) { scope = Objects.requireNonNull(value, "scope"); }
    String artifactName() { return artifactName; }
    void artifactName(String value) { artifactName = Objects.requireNonNullElse(value, ""); }
    String format() { return format; }
    String gameMode() { return gameMode; }
    String difficulty() { return difficulty; }
    int spawnX() { return spawnX == null ? 0 : spawnX; }
    int spawnY() { return spawnY == null ? 0 : spawnY; }
    int spawnZ() { return spawnZ == null ? 0 : spawnZ; }
    long timeOfDayTicks() { return timeOfDayTicks == null ? 0L : timeOfDayTicks; }
    String weather() { return weather; }
    boolean worldSettingsExpanded() { return worldSettingsExpanded; }
    boolean gameRulesExpanded() { return gameRulesExpanded; }
    void toggleWorldSettings() { worldSettingsExpanded = !worldSettingsExpanded; }
    void toggleGameRules() { gameRulesExpanded = !gameRulesExpanded; }

    void cycleFormat(List<String> formats) {
        if (formats == null || formats.isEmpty()) return;
        format = next(formats.toArray(String[]::new), format);
    }

    void cycleGameMode() { gameMode = next(GAME_MODES, gameMode); }
    void cycleDifficulty() { difficulty = next(DIFFICULTIES, difficulty); }
    void cycleWeather() { weather = next(WEATHERS, weather); }

    void cycleTime() {
        long current = timeOfDayTicks == null ? 0L : timeOfDayTicks;
        long[] presets = {1000L, 6000L, 13000L, 18000L};
        int best = 0;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < presets.length; i++) {
            long candidate = Math.abs(presets[i] - current);
            if (candidate < distance) {
                best = i;
                distance = candidate;
            }
        }
        timeOfDayTicks = presets[(best + 1) % presets.length];
    }

    void useSpawn(int x, int y, int z) {
        spawnX = x;
        spawnY = y;
        spawnZ = z;
    }

    String ruleValue(String name) { return gameRules.get(name); }
    boolean hasRule(String name) { return gameRules.containsKey(name); }

    void toggleBooleanRule(String name) {
        String value = gameRules.get(name);
        if (value == null) return;
        if (value.equalsIgnoreCase("true")) gameRules.put(name, "false");
        else if (value.equalsIgnoreCase("false")) gameRules.put(name, "true");
    }

    ExportSettingsWire.Settings toWire() {
        WorldControlWireProtocol.SettingsSnapshot base = Objects.requireNonNull(source, "source settings");
        Map<String, String> ruleOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : gameRules.entrySet()) {
            String original = sourceGameRules.get(entry.getKey());
            if (!Objects.equals(original, entry.getValue())) ruleOverrides.put(entry.getKey(), entry.getValue());
        }

        Integer outSpawnX = null;
        Integer outSpawnY = null;
        Integer outSpawnZ = null;
        if (spawnX != round(base.spawnX()) || spawnY != round(base.spawnY()) || spawnZ != round(base.spawnZ())) {
            outSpawnX = spawnX;
            outSpawnY = spawnY;
            outSpawnZ = spawnZ;
        }
        Long outTime = timeOfDayTicks != base.timeOfDayTicks() ? timeOfDayTicks : null;

        return new ExportSettingsWire.Settings(
                same(gameMode, base.defaultGameMode()) ? "" : gameMode,
                same(difficulty, base.difficulty()) ? "" : difficulty,
                outSpawnX, outSpawnY, outSpawnZ,
                outTime,
                same(weather, base.weather()) ? "" : weather,
                ruleOverrides,
                true
        );
    }

    static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static String ruleLabel(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (i > 0 && Character.isUpperCase(ch)) out.append(' ');
            out.append(ch);
        }
        String label = out.toString().replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
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

    private static String sanitizeArtifactName(String displayName) {
        String base = Objects.requireNonNullElse(displayName, "world").strip()
                .replaceAll("[^A-Za-z0-9._ -]+", "")
                .replace(' ', '-');
        while (base.contains("--")) base = base.replace("--", "-");
        base = base.replaceAll("^[. -]+|[. -]+$", "");
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = "world";
        return base + "-export";
    }
}
