package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TranslucentSortPolicy;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReference;

/** Avoids scheduling translucent sort tasks that vanilla would immediately cancel. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class BuiltChunkTranslucentSortMixin {
    @Shadow public AtomicReference<ChunkBuilder.NormalizedRelativePos> relativePos;
    @Shadow long sectionPos;
    @Shadow public abstract boolean hasTranslucentLayer();

    @Inject(method = "scheduleSort", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$skipRedundantSort(ChunkBuilder builder, CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || builder == null) return;

        boolean hasTranslucentLayer = this.hasTranslucentLayer();
        ChunkBuilder.NormalizedRelativePos current = ChunkBuilder.NormalizedRelativePos.of(
                builder.getCameraPosition(),
                this.sectionPos
        );
        boolean sameRelativePosition = current.equals(this.relativePos.get());

        if (TranslucentSortPolicy.shouldSkip(hasTranslucentLayer, sameRelativePosition, current.isOnCameraAxis())) {
            ChunkPipelineMetrics.recordAvoidedTranslucentSortTask();
            ci.cancel();
        }
    }
}
