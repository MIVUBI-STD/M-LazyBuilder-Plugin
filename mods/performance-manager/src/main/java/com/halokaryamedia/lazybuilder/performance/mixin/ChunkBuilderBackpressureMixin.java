package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkRebuildBackpressure;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies bounded backpressure to non-prioritized chunk tasks under sustained heavy frame pressure. */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderBackpressureMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$deferChunkTask(ChunkBuilder.BuiltChunk.Task task, CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        ChunkBuilder self = (ChunkBuilder) (Object) this;
        if (ChunkRebuildBackpressure.deferIfNeeded(self, task, PerformanceManagerClient.pressure())) {
            ci.cancel();
        }
    }

    @Inject(method = {"reset", "stop"}, at = @At("HEAD"))
    private void lazybuilder$cancelDeferredTasks(CallbackInfo ci) {
        ChunkRebuildBackpressure.cancel((ChunkBuilder) (Object) this);
    }
}
