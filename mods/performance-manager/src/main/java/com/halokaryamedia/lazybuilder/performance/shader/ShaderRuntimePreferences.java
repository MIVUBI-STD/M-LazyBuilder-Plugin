package com.halokaryamedia.lazybuilder.performance.shader;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted first-party shader runtime selection and per-pack option values. */
public record ShaderRuntimePreferences(
        String selectedPackId,
        boolean enabled,
        Map<String, String> optionValues
) {
    public ShaderRuntimePreferences {
        selectedPackId = selectedPackId == null ? "" : selectedPackId.trim();
        optionValues = optionValues == null ? Map.of() : Map.copyOf(optionValues);
    }

    public ShaderRuntimePreferences(String selectedPackId, boolean enabled) {
        this(selectedPackId, enabled, Map.of());
    }

    public static ShaderRuntimePreferences defaults() {
        return new ShaderRuntimePreferences("", false, Map.of());
    }

    public ShaderRuntimePreferences withSelectedPack(String packId) {
        return new ShaderRuntimePreferences(packId, false, optionValues);
    }

    public ShaderRuntimePreferences withEnabled(boolean enabled) {
        return new ShaderRuntimePreferences(selectedPackId, enabled, optionValues);
    }

    public ShaderRuntimePreferences withOption(String packId, String optionId, String value) {
        String key = optionKey(packId, optionId);
        if (key.isBlank()) return this;

        Map<String, String> updated = new LinkedHashMap<>(optionValues);
        updated.put(key, value == null ? "" : value.trim());
        return new ShaderRuntimePreferences(selectedPackId, enabled, Map.copyOf(updated));
    }

    public String optionValue(String packId, String optionId, String fallback) {
        return optionValues.getOrDefault(optionKey(packId, optionId), fallback);
    }

    static String optionKey(String packId, String optionId) {
        String pack = packId == null ? "" : packId.trim();
        String option = optionId == null ? "" : optionId.trim();
        if (pack.isBlank() || option.isBlank()) return "";
        return pack + "::" + option;
    }
}
