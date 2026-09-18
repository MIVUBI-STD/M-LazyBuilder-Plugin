package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.util.Identifier;

import java.util.regex.Pattern;

/**
 * Conservative runtime transformer for Minecraft's built-in terrain vertex shader.
 *
 * The transformer never ships or replaces Mojang shader source. It only augments the currently
 * loaded built-in vanilla terrain source when renderer ownership is first-party and the expected
 * ModelOffset contract is present. Custom resource-pack terrain sources remain untouched.
 */
public final class TerrainShaderSourceTransformer {
    private static final String VANILLA_PACK_ID = "vanilla";
    private static final String TERRAIN_RESOURCE_PATH = "shaders/core/terrain.vsh";
    private static final String TERRAIN_SHADER_PATH = "core/terrain";
    private static final String MODEL_OFFSET_DECLARATION = "uniform vec3 ModelOffset;";
    private static final String MARKER = "LazyBuilderDrawTransforms";
    private static final Pattern MODEL_OFFSET_TOKEN = Pattern.compile("\\bModelOffset\\b");
    private static final int MAX_TRANSFORMS = 1024;

    private static volatile boolean builtInTerrainSource;
    private static volatile boolean compileFallbackActive;
    private static volatile String status = "unseen";
    private static volatile long transformedCompiles;
    private static volatile long compileFallbacks;

    private TerrainShaderSourceTransformer() {
    }

    public static void observeResource(Identifier id, CompiledShader.Type type, String packId) {
        if (!isTerrainResource(id, type)) return;

        compileFallbackActive = false;
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        if (!renderer.terrainSubmissionSafe()) {
            builtInTerrainSource = false;
            status = "renderer-owned";
            return;
        }

        builtInTerrainSource = VANILLA_PACK_ID.equals(packId);
        status = builtInTerrainSource ? "vanilla-source" : "external-resource-pack";
    }

    public static String transform(Identifier id, CompiledShader.Type type, String source) {
        if (!isTerrainShader(id, type)) return source;
        if (compileFallbackActive || !builtInTerrainSource
                || !RendererCompatibility.detect().terrainSubmissionSafe()) {
            return source;
        }

        String transformed = transformSource(true, source);
        if (transformed == source || transformed.equals(source)) {
            status = "contract-missing";
            return source;
        }

        transformedCompiles++;
        status = "activated";
        return transformed;
    }

    public static void recordCompileFallback() {
        compileFallbacks++;
        compileFallbackActive = true;
        builtInTerrainSource = false;
        status = "compile-fallback";
    }

    static String transformSource(boolean allowed, String source) {
        if (!allowed || source == null || source.isEmpty() || source.contains(MARKER)) return source;

        int declarationIndex = source.indexOf(MODEL_OFFSET_DECLARATION);
        if (declarationIndex < 0) return source;
        int declarationEnd = declarationIndex + MODEL_OFFSET_DECLARATION.length();

        int versionEnd = source.indexOf('\n');
        if (versionEnd < 0 || !source.substring(0, versionEnd).trim().startsWith("#version")) return source;

        String tail = source.substring(declarationEnd);
        String replacedTail = MODEL_OFFSET_TOKEN.matcher(tail).replaceAll("lazybuilder_model_offset()");
        if (tail.equals(replacedTail)) return source;

        String extension = "\n#ifdef GL_ARB_shader_draw_parameters\n"
                + "#extension GL_ARB_shader_draw_parameters : enable\n"
                + "#endif\n";
        String contract = "\nlayout(std140) uniform LazyBuilderDrawTransforms {\n"
                + "    vec4 LazyBuilderModelOffsets[" + MAX_TRANSFORMS + "];\n"
                + "};\n"
                + "uniform int LazyBuilderDrawBase;\n"
                + "uniform int LazyBuilderMultiDrawEnabled;\n"
                + "vec3 lazybuilder_model_offset() {\n"
                + "#ifdef GL_ARB_shader_draw_parameters\n"
                + "    if (LazyBuilderMultiDrawEnabled != 0) {\n"
                + "        return LazyBuilderModelOffsets[LazyBuilderDrawBase + gl_DrawIDARB].xyz;\n"
                + "    }\n"
                + "#endif\n"
                + "    return ModelOffset;\n"
                + "}\n";

        return source.substring(0, versionEnd + 1)
                + extension
                + source.substring(versionEnd + 1, declarationEnd)
                + contract
                + replacedTail;
    }

    public static Snapshot snapshot() {
        return new Snapshot(status, transformedCompiles, compileFallbacks, compileFallbackActive);
    }

    public static String status() {
        return status;
    }

    public static void reset() {
        builtInTerrainSource = false;
        compileFallbackActive = false;
        status = "unseen";
        transformedCompiles = 0L;
        compileFallbacks = 0L;
    }

    private static boolean isTerrainResource(Identifier id, CompiledShader.Type type) {
        return id != null
                && type == CompiledShader.Type.VERTEX
                && "minecraft".equals(id.getNamespace())
                && TERRAIN_RESOURCE_PATH.equals(id.getPath());
    }

    private static boolean isTerrainShader(Identifier id, CompiledShader.Type type) {
        return id != null
                && type == CompiledShader.Type.VERTEX
                && "minecraft".equals(id.getNamespace())
                && TERRAIN_SHADER_PATH.equals(id.getPath());
    }

    public record Snapshot(
            String status,
            long transformedCompiles,
            long compileFallbacks,
            boolean compileFallbackActive
    ) {
    }
}
