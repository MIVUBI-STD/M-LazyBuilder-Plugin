package com.halokaryamedia.lazybuilder.performance.shader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single mutable owner for shader subsystem health.
 *
 * Keeping category errors in one owner prevents duplicated last-error state from drifting out of
 * sync with the underlying subsystem that actually failed.
 */
final class ShaderRuntimeHealth {
    private volatile String catalog = "";
    private volatile String compile = "";
    private volatile String terrain = "";
    private volatile String gbuffer = "";
    private volatile String shadow = "";
    private volatile String postProcess = "";
    private volatile String control = "";
    private volatile String preview = "";

    String catalog() { return catalog; }
    String compile() { return compile; }
    String terrain() { return terrain; }
    String gbuffer() { return gbuffer; }
    String shadow() { return shadow; }
    String postProcess() { return postProcess; }
    String control() { return control; }
    String preview() { return preview; }

    void catalog(String value) { catalog = safe(value); }
    void compile(String value) { compile = safe(value); }
    void terrain(String value) { terrain = safe(value); }
    void gbuffer(String value) { gbuffer = safe(value); }
    void shadow(String value) { shadow = safe(value); }
    void postProcess(String value) { postProcess = safe(value); }
    void control(String value) { control = safe(value); }
    void preview(String value) { preview = safe(value); }

    void clear() {
        catalog = "";
        compile = "";
        terrain = "";
        gbuffer = "";
        shadow = "";
        postProcess = "";
        control = "";
        preview = "";
    }

    String primary() {
        if (!catalog.isBlank()) return catalog;
        if (!compile.isBlank()) return compile;
        if (!terrain.isBlank()) return terrain;
        if (!gbuffer.isBlank()) return gbuffer;
        if (!shadow.isBlank()) return shadow;
        if (!postProcess.isBlank()) return postProcess;
        if (!control.isBlank()) return control;
        if (!preview.isBlank()) return preview;
        return "";
    }

    Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("catalog", catalog);
        values.put("compile", compile);
        values.put("terrain", terrain);
        values.put("gbuffer", gbuffer);
        values.put("shadow", shadow);
        values.put("postProcess", postProcess);
        values.put("control", control);
        values.put("preview", preview);
        return Map.copyOf(values);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
