package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses resolved block-layer allocators for the five fixed vanilla terrain layers. */
@Mixin(BlockBufferAllocatorStorage.class)
abstract class BlockBufferAllocatorStorageMixin {
    @Unique private BufferAllocator lazybuilder$solidAllocator;
    @Unique private BufferAllocator lazybuilder$cutoutMippedAllocator;
    @Unique private BufferAllocator lazybuilder$cutoutAllocator;
    @Unique private BufferAllocator lazybuilder$translucentAllocator;
    @Unique private BufferAllocator lazybuilder$tripwireAllocator;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseAllocator(RenderLayer layer, CallbackInfoReturnable<BufferAllocator> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        BufferAllocator cached = this.lazybuilder$getCachedAllocator(layer);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "get", at = @At("RETURN"))
    private void lazybuilder$rememberAllocator(RenderLayer layer, CallbackInfoReturnable<BufferAllocator> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        BufferAllocator allocator = cir.getReturnValue();
        if (allocator != null) {
            this.lazybuilder$cacheAllocator(layer, allocator);
        }
    }

    @Unique
    private BufferAllocator lazybuilder$getCachedAllocator(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return this.lazybuilder$solidAllocator;
        if (layer == RenderLayer.getCutoutMipped()) return this.lazybuilder$cutoutMippedAllocator;
        if (layer == RenderLayer.getCutout()) return this.lazybuilder$cutoutAllocator;
        if (layer == RenderLayer.getTranslucent()) return this.lazybuilder$translucentAllocator;
        if (layer == RenderLayer.getTripwire()) return this.lazybuilder$tripwireAllocator;
        return null;
    }

    @Unique
    private void lazybuilder$cacheAllocator(RenderLayer layer, BufferAllocator allocator) {
        if (layer == RenderLayer.getSolid()) this.lazybuilder$solidAllocator = allocator;
        else if (layer == RenderLayer.getCutoutMipped()) this.lazybuilder$cutoutMippedAllocator = allocator;
        else if (layer == RenderLayer.getCutout()) this.lazybuilder$cutoutAllocator = allocator;
        else if (layer == RenderLayer.getTranslucent()) this.lazybuilder$translucentAllocator = allocator;
        else if (layer == RenderLayer.getTripwire()) this.lazybuilder$tripwireAllocator = allocator;
    }
}
