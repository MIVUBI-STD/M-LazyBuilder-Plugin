package com.halokaryamedia.lazybuilder.performance.shader;

/** Explicit user-visible/runtime degradation level for the first-party shader pipeline. */
public enum ShaderRuntimeMode {
    DISABLED,
    FALLBACK,
    TERRAIN_ONLY,
    TERRAIN_SHADOW,
    TERRAIN_POST,
    FULL;

    static ShaderRuntimeMode evaluate(
            boolean configuredEnabled,
            boolean pipelinePresent,
            boolean terrainIntegrated,
            boolean shadowDeclared,
            boolean shadowReady,
            boolean postDeclared,
            boolean postApplied
    ) {
        if (!configuredEnabled || !pipelinePresent) return DISABLED;
        if (!terrainIntegrated) return FALLBACK;

        boolean shadow = shadowDeclared && shadowReady;
        boolean post = postDeclared && postApplied;

        if (shadow && post) return FULL;
        if (shadow) return TERRAIN_SHADOW;
        if (post) return TERRAIN_POST;
        return TERRAIN_ONLY;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
