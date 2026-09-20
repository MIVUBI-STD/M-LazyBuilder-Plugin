package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only block entities with a fresh, conservative occlusion result. */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    public abstract <E extends BlockEntity> BlockEntityRenderer<E> get(E blockEntity);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void lazybuilder$skipOccludedBlockEntity(
            E blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (!PerformanceManagerClient.preferences().blockEntityCulling()) return;
        BlockEntityRenderer<E> renderer = this.get(blockEntity);
        if (!PerformanceManagerClient.shouldRenderBlockEntity(blockEntity, renderer)) ci.cancel();
    }
}
