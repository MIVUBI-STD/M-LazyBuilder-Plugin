package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.IdentityProviderCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caches block color providers by block identity for the chunk-meshing hot path. */
@Mixin(BlockColors.class)
abstract class BlockColorsMixin {
    @Unique
    private final IdentityProviderCache<Block, BlockColorProvider> lazybuilder$providers = new IdentityProviderCache<>();

    @Inject(method = "registerColorProvider", at = @At("TAIL"))
    private void lazybuilder$trackProvider(
            BlockColorProvider provider,
            Block[] blocks,
            CallbackInfo ci
    ) {
        this.lazybuilder$providers.register(provider, blocks);
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$fastProviderLookup(
            BlockState state,
            BlockRenderView world,
            BlockPos pos,
            int tintIndex,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations() || state == null) return;

        BlockColorProvider provider = this.lazybuilder$providers.get(state.getBlock());
        if (provider != null) {
            cir.setReturnValue(provider.getColor(state, world, pos, tintIndex));
        }
    }
}
