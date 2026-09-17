package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkRebuildPolicy;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coalesces redundant vanilla chunk rebuild requests without changing rebuild priority semantics. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    public abstract boolean needsRebuild();

    @Shadow
    public abstract boolean needsImportantRebuild();

    @Inject(method = "scheduleRebuild(Z)V", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$coalesceRebuildRequest(boolean important, CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        if (ChunkRebuildPolicy.shouldSkip(this.needsRebuild(), this.needsImportantRebuild(), important)) {
            ci.cancel();
        }
    }
}
