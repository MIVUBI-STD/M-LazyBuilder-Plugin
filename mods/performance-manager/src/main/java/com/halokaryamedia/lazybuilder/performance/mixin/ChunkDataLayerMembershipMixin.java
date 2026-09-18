package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caches terrain-layer membership for the five fixed vanilla block render-layer identities. */
@Mixin(ChunkBuilder.ChunkData.class)
abstract class ChunkDataLayerMembershipMixin {
    @Unique private int lazybuilder$computedLayerBits;
    @Unique private int lazybuilder$nonEmptyLayerBits;

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseLayerMembership(RenderLayer layer, CallbackInfoReturnable<Boolean> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        int bit = lazybuilder$layerBit(layer);
        if (bit == 0 || (this.lazybuilder$computedLayerBits & bit) == 0) return;

        cir.setReturnValue((this.lazybuilder$nonEmptyLayerBits & bit) == 0);
    }

    @Inject(method = "isEmpty", at = @At("RETURN"))
    private void lazybuilder$rememberLayerMembership(RenderLayer layer, CallbackInfoReturnable<Boolean> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        int bit = lazybuilder$layerBit(layer);
        if (bit == 0) return;

        if (!cir.getReturnValueZ()) {
            this.lazybuilder$nonEmptyLayerBits |= bit;
        }
        this.lazybuilder$computedLayerBits |= bit;
    }

    @Unique
    private static int lazybuilder$layerBit(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return 1;
        if (layer == RenderLayer.getCutoutMipped()) return 1 << 1;
        if (layer == RenderLayer.getCutout()) return 1 << 2;
        if (layer == RenderLayer.getTranslucent()) return 1 << 3;
        if (layer == RenderLayer.getTripwire()) return 1 << 4;
        return 0;
    }
}
