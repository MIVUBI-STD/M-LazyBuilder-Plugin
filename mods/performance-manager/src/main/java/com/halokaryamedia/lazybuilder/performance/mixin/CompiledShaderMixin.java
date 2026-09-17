package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderSourceTransformer;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Augments only the built-in terrain vertex source before GL compilation. */
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
        GlStateManager.glShaderSource(
                shaderHandle,
                TerrainShaderSourceTransformer.transform(id, type, source)
        );
    }
}
