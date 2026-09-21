package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.capture.CaptureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes the fully rendered client framebuffer to the capture pipeline after each frame. */
@Mixin(GameRenderer.class)
abstract class GameRendererCaptureMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void lazybuilder$afterRenderedFrame(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;
        CaptureManager.onFrameRendered(client.getFramebuffer(), System.nanoTime());
    }
}
