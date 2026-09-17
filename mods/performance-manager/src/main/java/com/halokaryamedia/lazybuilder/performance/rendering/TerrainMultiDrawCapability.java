package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;

/** Explicit gate for the shader/input ownership required by true terrain multi-draw. */
public final class TerrainMultiDrawCapability {
    private TerrainMultiDrawCapability() {
    }

    public static Snapshot current() {
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        return evaluate(renderer.uncertain(), renderer.customRendererPresent(), renderer.irisPresent(), false);
    }

    static Snapshot evaluate(
            boolean compatibilityUncertain,
            boolean customRendererPresent,
            boolean irisPresent,
            boolean perDrawShaderDataReady
    ) {
        if (compatibilityUncertain) return new Snapshot(false, Reason.COMPATIBILITY_UNCERTAIN);
        if (customRendererPresent) return new Snapshot(false, Reason.CUSTOM_RENDERER_OWNER);
        if (irisPresent) return new Snapshot(false, Reason.IRIS_SHADER_OWNER);
        if (!perDrawShaderDataReady) return new Snapshot(false, Reason.MODEL_OFFSET_UNIFORM);
        return new Snapshot(true, Reason.READY);
    }

    public enum Reason {
        READY("ready"),
        MODEL_OFFSET_UNIFORM("model-offset-uniform"),
        IRIS_SHADER_OWNER("iris-shader-owner"),
        CUSTOM_RENDERER_OWNER("custom-renderer-owner"),
        COMPATIBILITY_UNCERTAIN("compatibility-uncertain");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Snapshot(boolean ready, Reason reason) {
        public String status() {
            return reason == null ? Reason.COMPATIBILITY_UNCERTAIN.id() : reason.id();
        }
    }
}
