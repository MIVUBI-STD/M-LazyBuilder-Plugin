package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuses consecutive identical text render-layer lookups inside one vanilla text drawer. */
@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
abstract class TextRendererDrawerMixin {
    @Unique
    private VertexConsumerProvider lazybuilder$lastProvider;

    @Unique
    private RenderLayer lazybuilder$lastRenderLayer;

    @Unique
    private VertexConsumer lazybuilder$lastVertexConsumer;

    @Redirect(
            method = "drawLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"
            )
    )
    private VertexConsumer lazybuilder$reuseConsecutiveBuffer(
            VertexConsumerProvider provider,
            RenderLayer layer
    ) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()) {
            return provider.getBuffer(layer);
        }
        if (this.lazybuilder$lastProvider == provider
                && this.lazybuilder$lastRenderLayer == layer
                && this.lazybuilder$lastVertexConsumer != null) {
            return this.lazybuilder$lastVertexConsumer;
        }

        this.lazybuilder$lastProvider = provider;
        this.lazybuilder$lastRenderLayer = layer;
        this.lazybuilder$lastVertexConsumer = provider.getBuffer(layer);
        return this.lazybuilder$lastVertexConsumer;
    }
}
