package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderCompileFallbackState;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderSourceTransformer;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Substitutes/augments terrain source and retries the exact original Minecraft
 * source if the transformed first-party stage fails compilation.
 */
@Mixin(CompiledShader.class)
abstract class CompiledShaderMixin {
    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/lang/String;)V"
            )
    )
    private static void lazybuilder$compileTerrainShaderContract(
            int shaderHandle,
            String source,
            Identifier id,
            CompiledShader.Type type,
            String originalSource
    ) {
        String transformed = TerrainShaderSourceTransformer.transform(id, type, source);
        if (!transformed.equals(source)) {
            TerrainShaderCompileFallbackState.rememberOriginal(
                    shaderHandle,
                    source,
                    type,
                    TerrainShaderSourceTransformer.firstPartyApplied(type)
            );
        }
        GlStateManager.glShaderSource(shaderHandle, transformed);
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;glCompileShader(I)V"
            )
    )
    private static void lazybuilder$retryOriginalTerrainShaderOnCompileFailure(
            int shaderHandle,
            Identifier id,
            CompiledShader.Type type,
            String source
    ) {
        GlStateManager.glCompileShader(shaderHandle);

        TerrainShaderCompileFallbackState.OriginalSource original =
                TerrainShaderCompileFallbackState.takeOriginal(shaderHandle);
        if (original == null) return;

        boolean success = GlStateManager.glGetShaderi(shaderHandle, 35713) != 0;
        if (success) {
            if (original.firstParty()) {
                TerrainShaderCompileFallbackState.rememberSuccessfulFirstPartyStage(
                        shaderHandle,
                        original.source(),
                        original.type()
                );
                TerrainShaderSourceTransformer.recordCompileSuccess(original.type());
            }
            return;
        }

        TerrainShaderSourceTransformer.recordCompileFallback(original.type());
        GlStateManager.glShaderSource(shaderHandle, original.source());
        GlStateManager.glCompileShader(shaderHandle);
        TerrainShaderCompileFallbackState.restoreOtherFirstPartyStages(original.type());
    }

}
