package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import com.mojang.blaze3d.systems.VertexSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Reuses per-layer BufferBuilder lookups inside one section build without sharing mutable state
 * between chunk worker threads.
 */
@Mixin(SectionBuilder.class)
abstract class SectionBuilderBufferLookupMixin {
    @Unique
    private static final ThreadLocal<LayerCache> lazybuilder$layerCache =
            ThreadLocal.withInitial(LayerCache::new);

    @Inject(method = "beginBufferBuilding", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseSectionBufferBuilder(
            Map<RenderLayer, BufferBuilder> builders,
            BlockBufferAllocatorStorage allocatorStorage,
            RenderLayer layer,
            CallbackInfoReturnable<BufferBuilder> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()
                || builders == null
                || layer == null) {
            return;
        }

        BufferBuilder cached = lazybuilder$layerCache.get().get(builders, layer);
        if (cached != null) {
            ChunkPipelineMetrics.recordSectionBuilderBufferLookupHit();
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "beginBufferBuilding", at = @At("RETURN"))
    private void lazybuilder$rememberSectionBufferBuilder(
            Map<RenderLayer, BufferBuilder> builders,
            BlockBufferAllocatorStorage allocatorStorage,
            RenderLayer layer,
            CallbackInfoReturnable<BufferBuilder> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()
                || builders == null
                || layer == null) {
            return;
        }

        BufferBuilder builder = cir.getReturnValue();
        if (builder != null) {
            lazybuilder$layerCache.get().remember(builders, layer, builder);
        }
    }

    @Inject(method = "build", at = @At("RETURN"))
    private void lazybuilder$releaseSectionBuildCache(
            ChunkSectionPos sectionPos,
            ChunkRendererRegion renderRegion,
            VertexSorter vertexSorter,
            BlockBufferAllocatorStorage allocatorStorage,
            CallbackInfoReturnable<SectionBuilder.RenderData> cir
    ) {
        lazybuilder$layerCache.remove();
    }

    @Unique
    private static final class LayerCache {
        private Map<RenderLayer, BufferBuilder> owner;
        private BufferBuilder solid;
        private BufferBuilder cutoutMipped;
        private BufferBuilder cutout;
        private BufferBuilder translucent;
        private BufferBuilder tripwire;

        BufferBuilder get(Map<RenderLayer, BufferBuilder> builders, RenderLayer layer) {
            ensureOwner(builders);
            if (layer == RenderLayer.getSolid()) return solid;
            if (layer == RenderLayer.getCutoutMipped()) return cutoutMipped;
            if (layer == RenderLayer.getCutout()) return cutout;
            if (layer == RenderLayer.getTranslucent()) return translucent;
            if (layer == RenderLayer.getTripwire()) return tripwire;
            return null;
        }

        void remember(Map<RenderLayer, BufferBuilder> builders, RenderLayer layer, BufferBuilder builder) {
            ensureOwner(builders);
            if (layer == RenderLayer.getSolid()) solid = builder;
            else if (layer == RenderLayer.getCutoutMipped()) cutoutMipped = builder;
            else if (layer == RenderLayer.getCutout()) cutout = builder;
            else if (layer == RenderLayer.getTranslucent()) translucent = builder;
            else if (layer == RenderLayer.getTripwire()) tripwire = builder;
        }

        private void ensureOwner(Map<RenderLayer, BufferBuilder> builders) {
            if (owner == builders) return;
            owner = builders;
            solid = null;
            cutoutMipped = null;
            cutout = null;
            translucent = null;
            tripwire = null;
        }
    }
}
