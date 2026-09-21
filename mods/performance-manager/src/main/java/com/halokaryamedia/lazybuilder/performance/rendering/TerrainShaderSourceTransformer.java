package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.shader.FirstPartyShaderRuntime;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.util.Identifier;

import java.util.regex.Pattern;

/**
 * Conservative terrain shader source owner.
 *
 * With no first-party shader pack it only augments Minecraft's built-in terrain
 * vertex source for guarded multi-draw. With an active compiled LazyBuilder pack,
 * it substitutes both native terrain stages, but only when the original source is
 * still vanilla-owned and no external renderer/shader owner has claimed the path.
 */
public final class TerrainShaderSourceTransformer {
    private static final String VANILLA_PACK_ID = "vanilla";
    private static final String TERRAIN_VERTEX_RESOURCE_PATH = "shaders/core/terrain.vsh";
    private static final String TERRAIN_FRAGMENT_RESOURCE_PATH = "shaders/core/terrain.fsh";
    private static final String TERRAIN_SHADER_PATH = "core/terrain";
    private static final String MODEL_OFFSET_DECLARATION = "uniform vec3 ModelOffset;";
    private static final String MARKER = "LazyBuilderDrawTransforms";
    private static final Pattern MODEL_OFFSET_TOKEN = Pattern.compile("\\bModelOffset\\b");
    private static final int MAX_TRANSFORMS = 1024;

    private static volatile boolean builtInVertexSource;
    private static volatile boolean builtInFragmentSource;
    private static volatile boolean firstPartyVertexApplied;
    private static volatile boolean firstPartyFragmentApplied;
    private static volatile boolean compileFallbackActive;
    private static volatile String status = "unseen";
    private static volatile long transformedCompiles;
    private static volatile long compileFallbacks;

    private TerrainShaderSourceTransformer() {
    }

    public static void observeResource(Identifier id, CompiledShader.Type type, String packId) {
        if (!isTerrainResource(id, type)) return;

        if (type == CompiledShader.Type.VERTEX) {
            PerformanceManagerClient.invalidateShaderTerrainForResourceReload();
            compileFallbackActive = false;
        }

        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        boolean safe = renderer.terrainSubmissionSafe();
        boolean vanilla = VANILLA_PACK_ID.equals(packId);

        if (type == CompiledShader.Type.VERTEX) {
            builtInVertexSource = safe && vanilla;
            firstPartyVertexApplied = false;
        } else if (type == CompiledShader.Type.FRAGMENT) {
            builtInFragmentSource = safe && vanilla;
            firstPartyFragmentApplied = false;
        }

        if (!safe) {
            status = "renderer-owned";
        } else if (!vanilla) {
            status = "external-resource-pack";
        } else {
            status = "vanilla-source";
        }
    }

    public static String transform(Identifier id, CompiledShader.Type type, String originalSource) {
        if (!isTerrainShader(id, type)) return originalSource;
        if (compileFallbackActive || !RendererCompatibility.detect().terrainSubmissionSafe()) {
            return originalSource;
        }

        boolean builtIn = type == CompiledShader.Type.VERTEX
                ? builtInVertexSource
                : builtInFragmentSource;
        if (!builtIn) return originalSource;

        boolean vertex = type == CompiledShader.Type.VERTEX;
        FirstPartyShaderRuntime.TerrainSource firstParty =
                PerformanceManagerClient.firstPartyTerrainSource(vertex);

        if (firstParty.available() && !firstParty.source().isBlank()) {
            String replacement = firstParty.source();
            if (vertex) {
                // Multi-draw remains an optional contract. A native pack without
                // ModelOffset still renders through the guarded single-draw path.
                String augmented = transformSource(true, replacement);
                if (!augmented.equals(replacement)) replacement = augmented;
                firstPartyVertexApplied = true;
                status = "first-party-vertex";
            } else {
                firstPartyFragmentApplied = true;
                status = "first-party-fragment";
            }
            transformedCompiles++;
            return replacement;
        }

        // No active first-party pack: preserve the existing LazyBuilder optimization
        // of vanilla terrain by augmenting only its vertex source.
        if (!vertex) return originalSource;

        String transformed = transformSource(true, originalSource);
        if (transformed == originalSource || transformed.equals(originalSource)) {
            status = "contract-missing";
            return originalSource;
        }

        transformedCompiles++;
        status = "activated";
        return transformed;
    }

    public static boolean firstPartyApplied(CompiledShader.Type type) {
        if (type == CompiledShader.Type.VERTEX) return firstPartyVertexApplied;
        if (type == CompiledShader.Type.FRAGMENT) return firstPartyFragmentApplied;
        return false;
    }

    public static void recordCompileSuccess(CompiledShader.Type type) {
        if (!firstPartyApplied(type)) return;
        PerformanceManagerClient.recordFirstPartyTerrainCompile(
                type == CompiledShader.Type.VERTEX,
                true,
                ""
        );
        status = type == CompiledShader.Type.VERTEX
                ? "first-party-vertex-compiled"
                : "first-party-fragment-compiled";
    }

    public static void recordCompileFallback(CompiledShader.Type type) {
        compileFallbacks++;
        compileFallbackActive = true;

        boolean firstParty = firstPartyApplied(type);
        if (firstParty) {
            PerformanceManagerClient.recordFirstPartyTerrainCompile(
                    type == CompiledShader.Type.VERTEX,
                    false,
                    "First-party terrain " + type.getName() + " source failed to compile; Minecraft source restored."
            );
        }

        if (type == CompiledShader.Type.VERTEX) firstPartyVertexApplied = false;
        if (type == CompiledShader.Type.FRAGMENT) firstPartyFragmentApplied = false;
        status = firstParty ? "first-party-compile-fallback" : "compile-fallback";
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
        return new Snapshot(
                status,
                transformedCompiles,
                compileFallbacks,
                compileFallbackActive,
                firstPartyVertexApplied,
                firstPartyFragmentApplied
        );
    }

    public static String status() {
        return status;
    }

    public static void invalidateForResourceReload() {
        builtInVertexSource = false;
        builtInFragmentSource = false;
        firstPartyVertexApplied = false;
        firstPartyFragmentApplied = false;
        compileFallbackActive = false;
        status = "shader-reload";
    }

    public static void reset() {
        builtInVertexSource = false;
        builtInFragmentSource = false;
        firstPartyVertexApplied = false;
        firstPartyFragmentApplied = false;
        compileFallbackActive = false;
        status = "unseen";
        transformedCompiles = 0L;
        compileFallbacks = 0L;
    }

    public static boolean isTerrainShaderId(Identifier id) {
        return id != null
                && "minecraft".equals(id.getNamespace())
                && TERRAIN_SHADER_PATH.equals(id.getPath());
    }

    private static boolean isTerrainResource(Identifier id, CompiledShader.Type type) {
        if (id == null || type == null || !"minecraft".equals(id.getNamespace())) return false;
        String expected = type == CompiledShader.Type.VERTEX
                ? TERRAIN_VERTEX_RESOURCE_PATH
                : TERRAIN_FRAGMENT_RESOURCE_PATH;
        return expected.equals(id.getPath());
    }

    private static boolean isTerrainShader(Identifier id, CompiledShader.Type type) {
        return type != null && isTerrainShaderId(id);
    }

    public record Snapshot(
            String status,
            long transformedCompiles,
            long compileFallbacks,
            boolean compileFallbackActive,
            boolean firstPartyVertexApplied,
            boolean firstPartyFragmentApplied
    ) {
    }
}
