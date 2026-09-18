package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuReclamationPolicy;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Reuses terrain buffers, tracks residency, and reclaims stale oversized allocations on region moves. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class BuiltChunkBufferLookupMixin {
    @Shadow @Final private Map<RenderLayer, VertexBuffer> buffers;

    @Unique private VertexBuffer lazybuilder$solidBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutMippedBuffer;
    @Unique private VertexBuffer lazybuilder$cutoutBuffer;
    @Unique private VertexBuffer lazybuilder$translucentBuffer;
    @Unique private VertexBuffer lazybuilder$tripwireBuffer;
    @Unique private long lazybuilder$previousSectionPos;

    @Shadow
    public abstract long getSectionPos();

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseTerrainBuffer(RenderLayer layer, CallbackInfoReturnable<VertexBuffer> cir) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || layer == null) return;

        VertexBuffer cached = this.lazybuilder$getCachedBuffer(layer);
        if (cached != null) {
            ChunkPipelineMetrics.recordTerrainBufferLookupHit();
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

    @Inject(method = "setSectionPos", at = @At("HEAD"))
    private void lazybuilder$capturePreviousSection(long sectionPos, CallbackInfo ci) {
        this.lazybuilder$previousSectionPos = this.getSectionPos();
    }

    @Inject(method = "setSectionPos", at = @At("TAIL"))
    private void lazybuilder$moveResidencyOwnership(long sectionPos, CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;

        if (RenderSystem.isOnRenderThread()) {
            this.lazybuilder$reclaimOversizedBuffers(this.lazybuilder$previousSectionPos, sectionPos);
        }
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
    private void lazybuilder$reclaimOversizedBuffers(long oldSectionPos, long newSectionPos) {
        if (oldSectionPos == newSectionPos) return;

        int oldX = ChunkSectionPos.unpackX(oldSectionPos);
        int oldY = ChunkSectionPos.unpackY(oldSectionPos);
        int oldZ = ChunkSectionPos.unpackZ(oldSectionPos);
        int newX = ChunkSectionPos.unpackX(newSectionPos);
        int newY = ChunkSectionPos.unpackY(newSectionPos);
        int newZ = ChunkSectionPos.unpackZ(newSectionPos);

        this.lazybuilder$reclaimLayer(RenderLayer.getSolid(), 0, oldX, oldY, oldZ, newX, newY, newZ, newSectionPos);
        this.lazybuilder$reclaimLayer(RenderLayer.getCutoutMipped(), 1, oldX, oldY, oldZ, newX, newY, newZ, newSectionPos);
        this.lazybuilder$reclaimLayer(RenderLayer.getCutout(), 2, oldX, oldY, oldZ, newX, newY, newZ, newSectionPos);
        this.lazybuilder$reclaimLayer(RenderLayer.getTranslucent(), 3, oldX, oldY, oldZ, newX, newY, newZ, newSectionPos);
        this.lazybuilder$reclaimLayer(RenderLayer.getTripwire(), 4, oldX, oldY, oldZ, newX, newY, newZ, newSectionPos);
    }

    @Unique
    private void lazybuilder$reclaimLayer(
            RenderLayer layer,
            int layerSlot,
            int oldX,
            int oldY,
            int oldZ,
            int newX,
            int newY,
            int newZ,
            long newSectionPos
    ) {
        VertexBuffer current = this.buffers.get(layer);
        if (current == null || current.isClosed()) return;

        long capacityBytes = TerrainGpuResidencyTracker.capacityBytes(current);
        if (!TerrainGpuReclamationPolicy.shouldReclaim(
                capacityBytes,
                oldX,
                oldY,
                oldZ,
                newX,
                newY,
                newZ
        )) {
            return;
        }

        TerrainGpuResidencyTracker.release(current);
        current.close();

        VertexBuffer replacement = new VertexBuffer(GlUsage.STATIC_WRITE);
        this.buffers.put(layer, replacement);
        this.lazybuilder$cacheBuffer(layer, replacement);
        TerrainGpuResidencyTracker.associate(replacement, newSectionPos, layerSlot);
        ChunkPipelineMetrics.recordTerrainGpuReclamation(capacityBytes);
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
