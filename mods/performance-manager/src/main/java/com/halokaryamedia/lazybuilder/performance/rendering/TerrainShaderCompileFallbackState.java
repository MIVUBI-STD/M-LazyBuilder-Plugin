package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.CompiledShader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime owner for transformed terrain shader fallback bookkeeping. */
public final class TerrainShaderCompileFallbackState {
    private static final Map<Integer, OriginalSource> ORIGINAL_SOURCES =
            new ConcurrentHashMap<>();
    private static final Map<CompiledShader.Type, RestorableStage> SUCCESSFUL_FIRST_PARTY_STAGES =
            new ConcurrentHashMap<>();

    private TerrainShaderCompileFallbackState() {
    }

    public static void rememberOriginal(
            int shaderHandle,
            String source,
            CompiledShader.Type type,
            boolean firstParty
    ) {
        ORIGINAL_SOURCES.put(
                shaderHandle,
                new OriginalSource(source, type, firstParty)
        );
    }

    public static OriginalSource takeOriginal(int shaderHandle) {
        return ORIGINAL_SOURCES.remove(shaderHandle);
    }

    public static void rememberSuccessfulFirstPartyStage(
            int shaderHandle,
            String originalSource,
            CompiledShader.Type type
    ) {
        SUCCESSFUL_FIRST_PARTY_STAGES.put(
                type,
                new RestorableStage(shaderHandle, originalSource, type)
        );
    }

    public static boolean restoreFirstPartyStagesForLinkFallback() {
        if (SUCCESSFUL_FIRST_PARTY_STAGES.isEmpty()) return false;

        boolean restored = false;
        for (RestorableStage stage : SUCCESSFUL_FIRST_PARTY_STAGES.values()) {
            GlStateManager.glShaderSource(stage.shaderHandle(), stage.originalSource());
            GlStateManager.glCompileShader(stage.shaderHandle());

            boolean success = GlStateManager.glGetShaderi(stage.shaderHandle(), 35713) != 0;
            if (success) {
                restored = true;
                TerrainShaderSourceTransformer.recordCompileFallback(stage.type());
            }
        }
        SUCCESSFUL_FIRST_PARTY_STAGES.clear();
        return restored;
    }

    public static void restoreOtherFirstPartyStages(CompiledShader.Type failedType) {
        for (RestorableStage stage : SUCCESSFUL_FIRST_PARTY_STAGES.values()) {
            if (stage.type() == failedType) continue;

            GlStateManager.glShaderSource(stage.shaderHandle(), stage.originalSource());
            GlStateManager.glCompileShader(stage.shaderHandle());
            TerrainShaderSourceTransformer.recordCompileFallback(stage.type());
        }
        SUCCESSFUL_FIRST_PARTY_STAGES.clear();
    }

    public static void clearAfterLink() {
        SUCCESSFUL_FIRST_PARTY_STAGES.clear();
    }

    public static void reset() {
        ORIGINAL_SOURCES.clear();
        SUCCESSFUL_FIRST_PARTY_STAGES.clear();
    }

    public record OriginalSource(
            String source,
            CompiledShader.Type type,
            boolean firstParty
    ) {
    }

    private record RestorableStage(
            int shaderHandle,
            String originalSource,
            CompiledShader.Type type
    ) {
    }
}
