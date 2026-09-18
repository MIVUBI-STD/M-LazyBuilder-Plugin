package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderSourceTransformer;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/** Augments built-in terrain source and retries the original source if augmentation fails to compile. */
@Mixin(CompiledShader.class)
abstract class CompiledShaderMixin {
    private static final Map<Integer, String> lazybuilder$originalSources = new HashMap<>();

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
            lazybuilder$originalSources.put(shaderHandle, source);
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
    private static void lazybuilder$retryVanillaTerrainShaderOnCompileFailure(
            int shaderHandle,
            Identifier id,
            CompiledShader.Type type,
            String source
    ) {
        GlStateManager.glCompileShader(shaderHandle);

        String original = lazybuilder$originalSources.remove(shaderHandle);
        if (original == null || GlStateManager.glGetShaderi(shaderHandle, 35713) != 0) return;

        TerrainShaderSourceTransformer.recordCompileFallback();
        GlStateManager.glShaderSource(shaderHandle, original);
        GlStateManager.glCompileShader(shaderHandle);
    }
}
