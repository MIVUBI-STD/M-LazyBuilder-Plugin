package com.halokaryamedia.lazybuilder.world.export;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Bounded shared wire value used by full-world and map-area export requests. */
public final class ExportSettingsWire {
    public static final int MAX_RULES = 64;
    private static final int MAX_STRING_BYTES = 192;
    private static final Pattern RULE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");

    private ExportSettingsWire() {}

    /** Empty/absent fields mean inherit the staged source value. */
    public record Settings(
            String gameMode,
            String difficulty,
            Integer spawnX,
            Integer spawnY,
            Integer spawnZ,
            Long timeOfDayTicks,
            String weather,
            Map<String, String> gameRules,
            boolean optimizeOutput
    ) {
        /** Compatibility constructor for the first export-settings wire shape. */
        public Settings(String gameMode, String difficulty, Map<String, String> gameRules, boolean optimizeOutput) {
            this(gameMode, difficulty, null, null, null, null, "", gameRules, optimizeOutput);
        }

        public Settings(String gameMode, String difficulty, Map<String, String> gameRules) {
            this(gameMode, difficulty, null, null, null, null, "", gameRules, false);
        }

        public Settings {
            gameMode = normalizeOptional(gameMode, "gameMode");
            difficulty = normalizeOptional(difficulty, "difficulty");
            weather = normalizeOptional(weather, "weather");

            boolean anySpawn = spawnX != null || spawnY != null || spawnZ != null;
            boolean fullSpawn = spawnX != null && spawnY != null && spawnZ != null;
            if (anySpawn && !fullSpawn) throw new IllegalArgumentException("Spawn override must include X, Y, and Z");
            if (timeOfDayTicks != null && (timeOfDayTicks < 0 || timeOfDayTicks >= 24000)) {
                throw new IllegalArgumentException("Export time must be in range 0..23999");
            }

            Map<String, String> normalizedRules = new LinkedHashMap<>();
            Map<String, String> sourceRules = gameRules == null ? Map.of() : gameRules;
            for (Map.Entry<String, String> entry : sourceRules.entrySet()) {
                String name = requireRuleName(entry.getKey());
                String value = requireRuleValue(entry.getValue(), name);
                normalizedRules.put(name, value);
            }
            if (normalizedRules.size() > MAX_RULES) throw new IllegalArgumentException("Too many export game rules");
            gameRules = Map.copyOf(normalizedRules);
        }

        /** Legacy client behavior: inherit settings and do not force a converter pass. */
        public static Settings inherit() {
            return new Settings("", "", null, null, null, null, "", Map.of(), false);
        }

        /** Export workspace behavior: inherit by default while enabling automatic output cleanup. */
        public static Settings workspaceDefaults() {
            return new Settings("", "", null, null, null, null, "", Map.of(), true);
        }
    }

    public static void write(DataOutputStream out, Settings settings) throws IOException {
        Objects.requireNonNull(out, "out");
        Settings value = Objects.requireNonNull(settings, "settings");
        out.writeBoolean(value.optimizeOutput());
        writeOptionalString(out, value.gameMode());
        writeOptionalString(out, value.difficulty());
        out.writeBoolean(value.spawnX() != null);
        if (value.spawnX() != null) {
            out.writeInt(value.spawnX());
            out.writeInt(value.spawnY());
            out.writeInt(value.spawnZ());
        }
        out.writeBoolean(value.timeOfDayTicks() != null);
        if (value.timeOfDayTicks() != null) out.writeLong(value.timeOfDayTicks());
        writeOptionalString(out, value.weather());
        out.writeShort(value.gameRules().size());
        for (Map.Entry<String, String> rule : value.gameRules().entrySet()) {
            writeString(out, rule.getKey());
            writeString(out, rule.getValue());
        }
    }

    public static Settings read(DataInputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        boolean optimizeOutput = in.readBoolean();
        String gameMode = readOptionalString(in);
        String difficulty = readOptionalString(in);
        Integer spawnX = null;
        Integer spawnY = null;
        Integer spawnZ = null;
        if (in.readBoolean()) {
            spawnX = in.readInt();
            spawnY = in.readInt();
            spawnZ = in.readInt();
        }
        Long time = in.readBoolean() ? in.readLong() : null;
        String weather = readOptionalString(in);
        int ruleCount = in.readUnsignedShort();
        if (ruleCount > MAX_RULES) throw new IOException("Too many export game rules");
        Map<String, String> rules = new LinkedHashMap<>();
        for (int i = 0; i < ruleCount; i++) {
            String name = readString(in);
            String value = readString(in);
            if (rules.put(name, value) != null) throw new IOException("Duplicate export game rule: " + name);
        }
        try {
            return new Settings(gameMode, difficulty, spawnX, spawnY, spawnZ, time, weather, rules, optimizeOutput);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid export settings: " + exception.getMessage(), exception);
        }
    }

    private static String normalizeOptional(String value, String label) {
        if (value == null) return "";
        String normalized = value.strip();
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private static String requireRuleName(String value) {
        String name = Objects.requireNonNull(value, "gameRule name").strip();
        if (!RULE_NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid game rule name: " + name);
        return name;
    }

    private static String requireRuleValue(String value, String name) {
        String normalized = Objects.requireNonNull(value, "gameRule value").strip();
        if (normalized.isEmpty() || normalized.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid game rule value: " + name);
        }
        return normalized;
    }

    private static void writeOptionalString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Export setting string is too long");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readOptionalString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw new IOException("Export setting string length is invalid");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Export setting string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_STRING_BYTES) throw new IOException("Export setting string length is invalid");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 1 || length > MAX_STRING_BYTES) throw new IOException("Export setting string length is invalid");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Export setting string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
