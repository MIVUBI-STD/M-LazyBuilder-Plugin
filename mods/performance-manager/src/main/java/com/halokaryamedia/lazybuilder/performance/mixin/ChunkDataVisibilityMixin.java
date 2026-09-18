package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caches the 36 directional section-visibility pairs after vanilla computes them once. */
@Mixin(ChunkBuilder.ChunkData.class)
abstract class ChunkDataVisibilityMixin {
    private static final int DIRECTION_COUNT = 6;

    @Unique
    private volatile long lazybuilder$computedVisibilityPairs;

    @Unique
    private volatile long lazybuilder$visiblePairs;

    @Inject(method = "isVisibleThrough", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$reuseVisibilityPair(
            Direction from,
            Direction to,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || from == null || to == null) return;

        long bit = lazybuilder$visibilityBit(from, to);
        if ((this.lazybuilder$computedVisibilityPairs & bit) == 0L) return;

        ChunkPipelineMetrics.recordSectionVisibilityCacheHit();
        cir.setReturnValue((this.lazybuilder$visiblePairs & bit) != 0L);
    }

    @Inject(method = "isVisibleThrough", at = @At("RETURN"))
    private void lazybuilder$rememberVisibilityPair(
            Direction from,
            Direction to,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || from == null || to == null) return;

        long bit = lazybuilder$visibilityBit(from, to);
        if (cir.getReturnValueZ()) {
            this.lazybuilder$visiblePairs |= bit;
        }
        this.lazybuilder$computedVisibilityPairs |= bit;
    }

    @Unique
    private static long lazybuilder$visibilityBit(Direction from, Direction to) {
        return 1L << (from.ordinal() * DIRECTION_COUNT + to.ordinal());
    }
}
