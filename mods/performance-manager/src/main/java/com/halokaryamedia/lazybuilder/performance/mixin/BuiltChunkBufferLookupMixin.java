package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses resolved terrain buffers and tracks their section/region residency ownership. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class BuiltChunkBufferLookupMixin {
    @Unique private VertexBuffer lazybuilder$solidBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutMippedBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutBuffer;
    @Unique private VertexBuffer lazybuilder$translucentBuffer;
    @Unique private VertexBuffer lazybuilder$tripwireBuffer;

    @Shadow
    public abstract long getSectionPos();

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseTerrainBuffer(RenderLayer layer, CallbackInfoReturnable<VertexBuffer> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        VertexBuffer cached = this.lazybuilder$getCachedBuffer(layer);
        if (cached != null) {
            TerrainGpuResidencyTracker.associate(cached, this.getSectionPos(), lazybuilder$layerSlot(layer));
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void lazybuilder$rememberTerrainBuffer(RenderLayer layer, CallbackInfoReturnable<VertexBuffer> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        VertexBuffer buffer = cir.getReturnValue();
        if (buffer != null) {
            this.lazybuilder$cacheBuffer(layer, buffer);
            TerrainGpuResidencyTracker.associate(buffer, this.getSectionPos(), lazybuilder$layerSlot(layer));
        }
    }

    @Inject(method = "setSectionPos", at = @At("TAIL"))
    private void lazybuilder$moveResidencyOwnership(long sectionPos, CallbackInfo ci) {
        this.lazybuilder$associateCachedBuffers(sectionPos);
    }

    @Inject(method = "delete", at = @At("TAIL"))
    private void lazybuilder$releaseResidencyOwnership(CallbackInfo ci) {
        TerrainGpuResidencyTracker.release(this.lazybuilder$solidBuffer);
        TerrainGpuResidencyTracker.release(this.lazybuilder$cutoutMippedBuffer);
        TerrainGpuResidencyTracker.release(this.lazybuilder$cutoutBuffer);
        TerrainGpuResidencyTracker.release(this.lazybuilder$translucentBuffer);
        TerrainGpuResidencyTracker.release(this.lazybuilder$tripwireBuffer);
    }

    @Unique
    private void lazybuilder$associateCachedBuffers(long sectionPos) {
        if (this.lazybuilder$solidBuffer != null) {
            TerrainGpuResidencyTracker.associate(this.lazybuilder$solidBuffer, sectionPos, 0);
        }
        if (this.lazybuilder$cutoutMippedBuffer != null) {
            TerrainGpuResidencyTracker.associate(this.lazybuilder$cutoutMippedBuffer, sectionPos, 1);
        }
        if (this.lazybuilder$cutoutBuffer != null) {
            TerrainGpuResidencyTracker.associate(this.lazybuilder$cutoutBuffer, sectionPos, 2);
        }
        if (this.lazybuilder$translucentBuffer != null) {
            TerrainGpuResidencyTracker.associate(this.lazybuilder$translucentBuffer, sectionPos, 3);
        }
        if (this.lazybuilder$tripwireBuffer != null) {
            TerrainGpuResidencyTracker.associate(this.lazybuilder$tripwireBuffer, sectionPos, 4);
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

    @Unique
    private static int lazybuilder$layerSlot(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) return 0;
        if (layer == RenderLayer.getCutoutMipped()) return 1;
        if (layer == RenderLayer.getCutout()) return 2;
        if (layer == RenderLayer.getTranslucent()) return 3;
        if (layer == RenderLayer.getTripwire()) return 4;
        return -1;
    }
}
