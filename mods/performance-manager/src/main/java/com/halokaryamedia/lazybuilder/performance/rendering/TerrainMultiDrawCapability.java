package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import net.minecraft.client.gl.ShaderProgram;

/** Explicit gate for the shader/input ownership required by true terrain multi-draw. */
public final class TerrainMultiDrawCapability {
    private TerrainMultiDrawCapability() {
    }

    public static Snapshot current() {
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        return evaluate(renderer.uncertain(), renderer.customRendererPresent(), renderer.irisPresent(), false);
    }

    public static Snapshot current(ShaderProgram program) {
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        if (renderer.uncertain()) return new Snapshot(false, Reason.COMPATIBILITY_UNCERTAIN);
        if (renderer.customRendererPresent()) return new Snapshot(false, Reason.CUSTOM_RENDERER_OWNER);
        if (renderer.irisPresent()) return new Snapshot(false, Reason.IRIS_SHADER_OWNER);

        TerrainPerDrawShaderBackend.Probe probe = TerrainPerDrawShaderBackend.probe(program, 0);
        return switch (probe.status()) {
            case "ready" -> new Snapshot(true, Reason.READY);
            case "draw-id-unsupported" -> new Snapshot(false, Reason.DRAW_ID_UNSUPPORTED);
            case "transform-block-missing" -> new Snapshot(false, Reason.TRANSFORM_BLOCK_MISSING);
            case "transform-block-too-small" -> new Snapshot(false, Reason.TRANSFORM_BLOCK_TOO_SMALL);
            default -> new Snapshot(false, Reason.MODEL_OFFSET_UNIFORM);
        };
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
        DRAW_ID_UNSUPPORTED("draw-id-unsupported"),
        TRANSFORM_BLOCK_MISSING("transform-block-missing"),
        TRANSFORM_BLOCK_TOO_SMALL("transform-block-too-small"),
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
