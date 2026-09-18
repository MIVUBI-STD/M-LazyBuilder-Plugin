package com.halokaryamedia.lazybuilder.performance.mixin;

import com.google.common.collect.ImmutableMap;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainShaderSourceTransformer;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Records whether the active Minecraft terrain shader source still comes from the built-in pack. */
@Mixin(ShaderLoader.class)
abstract class ShaderLoaderSourceMixin {
    @Inject(method = "loadShaderSource", at = @At("HEAD"))
    private static void lazybuilder$observeTerrainShaderSource(
            Identifier id,
            Resource resource,
            CompiledShader.Type type,
            Map<Identifier, Resource> allResources,
            ImmutableMap.Builder builder,
            CallbackInfo ci
    ) {
        TerrainShaderSourceTransformer.observeResource(
                id,
                type,
                resource == null ? "" : resource.getPackId()
        );
    }
}
