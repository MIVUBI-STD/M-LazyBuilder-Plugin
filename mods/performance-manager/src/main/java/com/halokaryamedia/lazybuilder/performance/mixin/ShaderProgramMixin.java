package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderSourceTransformer;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.GL20C;

/**
 * Successful RETURN from ShaderProgram.create proves the compiled terrain stages
 * linked into Minecraft's real terrain program.
 */
@Mixin(ShaderProgram.class)
abstract class ShaderProgramMixin {
    @Redirect(
            method = "create",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;glLinkProgram(I)V"
            )
    )
    private static void lazybuilder$linkTerrainWithVanillaFallback(
            int program,
            CompiledShader vertexShader,
            CompiledShader fragmentShader,
            VertexFormat format
    ) {
        GlStateManager.glLinkProgram(program);

        if (GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS) != 0
                || vertexShader == null
                || fragmentShader == null
                || !TerrainShaderSourceTransformer.isTerrainShaderId(vertexShader.getId())
                || !TerrainShaderSourceTransformer.isTerrainShaderId(fragmentShader.getId())) {
            return;
        }

        if (CompiledShaderMixin.lazybuilder$restoreFirstPartyStagesForLinkFallback()) {
            GlStateManager.glLinkProgram(program);
        }
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void lazybuilder$recordTerrainProgramLink(
            CompiledShader vertexShader,
            CompiledShader fragmentShader,
            VertexFormat format,
            CallbackInfoReturnable<ShaderProgram> cir
    ) {
        if (cir.getReturnValue() == null
                || vertexShader == null
                || fragmentShader == null
                || !TerrainShaderSourceTransformer.isTerrainShaderId(vertexShader.getId())
                || !TerrainShaderSourceTransformer.isTerrainShaderId(fragmentShader.getId())) {
            return;
        }

        PerformanceManagerClient.recordFirstPartyTerrainProgramLinked();
        CompiledShaderMixin.lazybuilder$clearTerrainFallbackStateAfterLink();
    }
}
