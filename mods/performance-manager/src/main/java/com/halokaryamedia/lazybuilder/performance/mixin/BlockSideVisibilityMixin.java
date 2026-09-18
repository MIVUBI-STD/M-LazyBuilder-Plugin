package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.BlockSideVisibilityCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caches the pure vanilla block-side visibility decision used heavily during chunk meshing. */
@Mixin(Block.class)
abstract class BlockSideVisibilityMixin {
    @Unique
    private static final BlockSideVisibilityCache lazybuilder$sideVisibility = new BlockSideVisibilityCache();

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void lazybuilder$reuseSideVisibility(
            BlockState state,
            BlockState otherState,
            Direction side,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        Boolean cached = lazybuilder$sideVisibility.get(state, otherState, side);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "shouldDrawSide", at = @At("RETURN"))
    private static void lazybuilder$rememberSideVisibility(
            BlockState state,
            BlockState otherState,
            Direction side,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) return;
        lazybuilder$sideVisibility.put(state, otherState, side, cir.getReturnValue());
    }
}
