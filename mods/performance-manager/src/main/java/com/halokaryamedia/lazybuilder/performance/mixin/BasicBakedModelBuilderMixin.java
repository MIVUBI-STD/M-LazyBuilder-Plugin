package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.memory.MemoryDeduplicator;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Canonicalizes vanilla baked-quad vertex arrays while models are being assembled. */
@Mixin(BasicBakedModel.Builder.class)
abstract class BasicBakedModelBuilderMixin {
    @Inject(
            method = "addQuad(Lnet/minecraft/client/render/model/BakedQuad;)Lnet/minecraft/client/render/model/BasicBakedModel$Builder;",
            at = @At("HEAD")
    )
    private void lazybuilder$deduplicateQuad(
            BakedQuad quad,
            CallbackInfoReturnable<?> cir
    ) {
        MemoryDeduplicator.deduplicate(quad);
    }

    @Inject(
            method = "addQuad(Lnet/minecraft/util/math/Direction;Lnet/minecraft/client/render/model/BakedQuad;)Lnet/minecraft/client/render/model/BasicBakedModel$Builder;",
            at = @At("HEAD")
    )
    private void lazybuilder$deduplicateFaceQuad(
            Direction side,
            BakedQuad quad,
            CallbackInfoReturnable<?> cir
    ) {
        MemoryDeduplicator.deduplicate(quad);
    }
}
