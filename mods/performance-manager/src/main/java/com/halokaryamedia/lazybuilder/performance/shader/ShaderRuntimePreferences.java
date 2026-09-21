package com.halokaryamedia.lazybuilder.performance.shader;

/** Persisted first-party shader runtime selection. */
public record ShaderRuntimePreferences(
        String selectedPackId,
        boolean enabled
) {
    public ShaderRuntimePreferences {
        selectedPackId = selectedPackId == null ? "" : selectedPackId.trim();
    }

    public static ShaderRuntimePreferences defaults() {
        return new ShaderRuntimePreferences("", false);
    }

    public ShaderRuntimePreferences withSelectedPack(String packId) {
        return new ShaderRuntimePreferences(packId, false);
    }

    public ShaderRuntimePreferences withEnabled(boolean enabled) {
        return new ShaderRuntimePreferences(selectedPackId, enabled);
    }
}
