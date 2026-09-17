package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses resolved terrain buffers for the five fixed vanilla block render-layer identities. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class BuiltChunkBufferLookupMixin {
    @Unique private VertexBuffer lazybuilder$solidBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutMippedBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutBuffer;
    @Unique private VertexBuffer lazybuilder$translucentBuffer;
    @Unique private VertexBuffer lazybuilder$tripwireBuffer;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseTerrainBuffer(RenderLayer layer, CallbackInfoReturnable<VertexBuffer> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        VertexBuffer cached = this.lazybuilder$getCachedBuffer(layer);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void lazybuilder$rememberTerrainBuffer(RenderLayer layer, CallbackInfoReturnable<VertexBuffer> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        VertexBuffer buffer = cir.getReturnValue();
        if (buffer != null) {
            this.lazybuilder$cacheBuffer(layer, buffer);
        }
    }

    @Unique
    private VertexBuffer lazybuilder$getCachedBuffer(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return this.lazybuilder$solidBuffer;
        if (layer == RenderLayer.getCutoutMipped()) return this.lazybuilder$cutoutMippedBuffer;
        if (layer == RenderLayer.getCutout()) return this.lazybuilder$cutoutBuffer;
        if (layer == RenderLayer.getTranslucent()) return this.lazybuilder$translucentBuffer;
        if (layer == RenderLayer.getTripwire()) return this.lazybuilder$tripwireBuffer;
        return null;
    }

    @Unique
    private void lazybuilder$cacheBuffer(RenderLayer layer, VertexBuffer buffer) {
        if (layer == RenderLayer.getSolid()) this.lazybuilder$solidBuffer = buffer;
        else if (layer == RenderLayer.getCutoutMipped()) this.lazybuilder$cutoutMippedBuffer = buffer;
        else if (layer == RenderLayer.getCutout()) this.lazybuilder$cutoutBuffer = buffer;
        else if (layer == RenderLayer.getTranslucent()) this.lazybuilder$translucentBuffer = buffer;
        else if (layer == RenderLayer.getTripwire()) this.lazybuilder$tripwireBuffer = buffer;
    }
}
