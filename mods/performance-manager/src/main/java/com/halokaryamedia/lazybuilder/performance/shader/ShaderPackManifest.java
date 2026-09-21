package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.Properties;

/**
 * Optional shader.properties metadata for LazyBuilder-native shader packs.
 *
 * Supported option types are deliberately small and deterministic:
 * boolean, int, and float. Options become GLSL defines named LB_OPT_<ID>.
 */
public record ShaderPackManifest(
        String name,
        String author,
        String description,
        List<Option> options
) {
    private static final String FILE = "shader.properties";

    public ShaderPackManifest {
        name = clean(name);
        author = clean(author);
        description = clean(description);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ShaderPackManifest load(ShaderPackSource source, String fallbackName)
            throws IOException {
        if (!source.exists(FILE)) {
            return new ShaderPackManifest(fallbackName, "", "", List.of());
        }

        Properties properties = new Properties();
        properties.load(new StringReader(source.readText(FILE)));

        List<Option> options = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("option.") || !key.endsWith(".type")) continue;
            String id = key.substring("option.".length(), key.length() - ".type".length()).trim();
            if (!validId(id)) continue;

            Option option = parseOption(properties, id);
            if (option != null) options.add(option);
        }
        options.sort(Comparator.comparing(Option::id));

        Set<String> defineNames = new HashSet<>();
        for (Option option : options) {
            if (!defineNames.add(option.defineName())) {
                throw new IOException(
                        "Shader option define collision: " + option.defineName()
                );
            }
        }

        return new ShaderPackManifest(
                properties.getProperty("name", fallbackName),
                properties.getProperty("author", ""),
                properties.getProperty("description", ""),
                options
        );
    }

    public Map<String, String> defines(
            String packId,
            ShaderRuntimePreferences preferences
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Option option : options) {
            String stored = preferences.optionValue(packId, option.id(), option.defaultValue());
            String value = option.sanitize(stored);
            result.put(option.defineName(), option.glslLiteral(value));
        }
        return Map.copyOf(result);
    }

    private static Option parseOption(Properties properties, String id) {
        Type type;
        try {
            type = Type.valueOf(properties
                    .getProperty("option." + id + ".type", "")
                    .trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        String prefix = "option." + id + ".";
        String label = clean(properties.getProperty(prefix + "label", id));
        String defaultValue = clean(properties.getProperty(prefix + "default", ""));

        return switch (type) {
            case BOOLEAN -> {
                String normalized = normalizeBoolean(defaultValue);
                if (normalized == null) yield null;
                yield new Option(id, label, type, normalized, "", "", "");
            }
            case INT -> {
                Integer def = parseInt(defaultValue);
                Integer min = parseInt(properties.getProperty(prefix + "min", ""));
                Integer max = parseInt(properties.getProperty(prefix + "max", ""));
                Integer step = parseInt(properties.getProperty(prefix + "step", "1"));
                if (def == null || min == null || max == null || step == null
                        || min > max || step <= 0) yield null;
                int sanitizedDefault = quantizeInt(def, min, max, step);
                yield new Option(
                        id, label, type,
                        Integer.toString(sanitizedDefault),
                        Integer.toString(min),
                        Integer.toString(max),
                        Integer.toString(step)
                );
            }
            case FLOAT -> {
                Double def = parseDouble(defaultValue);
                Double min = parseDouble(properties.getProperty(prefix + "min", ""));
                Double max = parseDouble(properties.getProperty(prefix + "max", ""));
                Double step = parseDouble(properties.getProperty(prefix + "step", ""));
                if (def == null || min == null || max == null || step == null
                        || !Double.isFinite(def) || !Double.isFinite(min)
                        || !Double.isFinite(max) || !Double.isFinite(step)
                        || min > max || step <= 0.0) yield null;
                double sanitizedDefault = quantizeDouble(def, min, max, step);
                yield new Option(
                        id, label, type,
                        decimal(sanitizedDefault),
                        decimal(min),
                        decimal(max),
                        decimal(step)
                );
            }
        };
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    }

    private static String normalizeBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return "true";
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return "false";
        return null;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int quantizeInt(int value, int min, int max, int step) {
        int clamped = Math.max(min, Math.min(max, value));
        long slots = Math.round((clamped - (double) min) / step);
        long quantized = (long) min + slots * step;
        return (int) Math.max(min, Math.min(max, quantized));
    }

    private static double quantizeDouble(double value, double min, double max, double step) {
        double clamped = Math.max(min, Math.min(max, value));
        double slots = Math.rint((clamped - min) / step);
        double quantized = min + slots * step;
        return Math.max(min, Math.min(max, quantized));
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Type {
        BOOLEAN,
        INT,
        FLOAT
    }

    public record Option(
            String id,
            String label,
            Type type,
            String defaultValue,
            String min,
            String max,
            String step
    ) {
        public Option {
            id = clean(id);
            label = clean(label);
            type = type == null ? Type.BOOLEAN : type;
            defaultValue = clean(defaultValue);
            min = clean(min);
            max = clean(max);
            step = clean(step);
        }

        public String defineName() {
            return "LB_OPT_" + id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        }

        public String sanitize(String raw) {
            return switch (type) {
                case BOOLEAN -> {
                    String normalized = normalizeBoolean(clean(raw));
                    yield normalized == null ? defaultValue : normalized;
                }
                case INT -> {
                    Integer value = parseInt(clean(raw));
                    int fallback = parseInt(defaultValue);
                    int lo = parseInt(min);
                    int hi = parseInt(max);
                    int increment = parseInt(step);
                    yield Integer.toString(quantizeInt(
                            value == null ? fallback : value,
                            lo, hi, increment
                    ));
                }
                case FLOAT -> {
                    Double value = parseDouble(clean(raw));
                    double fallback = parseDouble(defaultValue);
                    double lo = parseDouble(min);
                    double hi = parseDouble(max);
                    double increment = parseDouble(step);
                    yield decimal(quantizeDouble(
                            value == null || !Double.isFinite(value) ? fallback : value,
                            lo, hi, increment
                    ));
                }
            };
        }

        public String glslLiteral(String sanitized) {
            return switch (type) {
                case BOOLEAN -> "true".equals(sanitize(sanitized)) ? "1" : "0";
                case INT -> sanitize(sanitized);
                case FLOAT -> {
                    String value = sanitize(sanitized);
                    yield value.contains(".") || value.contains("e") || value.contains("E")
                            ? value
                            : value + ".0";
                }
            };
        }
    }
}
