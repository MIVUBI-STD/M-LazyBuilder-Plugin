package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.BlockBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records vanilla chunk-buffer pool starvation without changing pool behavior. */
@Mixin(BlockBufferBuilderPool.class)
abstract class BlockBufferBuilderPoolMixin {
    @Inject(method = "acquire", at = @At("RETURN"))
    private void lazybuilder$recordAcquireMiss(CallbackInfoReturnable<BlockBufferAllocatorStorage> cir) {
        if (cir.getReturnValue() == null) {
            ChunkPipelineMetrics.recordBufferAcquireMiss();
        }
    }
}
