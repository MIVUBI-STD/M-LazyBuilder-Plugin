package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.capture.CaptureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes the fully rendered client framebuffer to the capture pipeline after each frame. */
@Mixin(GameRenderer.class)
abstract class GameRendererCaptureMixin {
    @Shadow private boolean renderHand;
    @Shadow private boolean blockOutlineEnabled;

    @Unique private boolean lazybuilder$cleanCaptureActive;
    @Unique private boolean lazybuilder$previousHudHidden;
    @Unique private boolean lazybuilder$previousRenderHand;
    @Unique private boolean lazybuilder$previousBlockOutline;

    @Inject(method = "render", at = @At("HEAD"))
    private void lazybuilder$prepareCleanCapture(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !CaptureManager.consumeCleanScreenshotRequest()) return;

        lazybuilder$cleanCaptureActive = true;
        lazybuilder$previousHudHidden = client.options.hudHidden;
        lazybuilder$previousRenderHand = renderHand;
        lazybuilder$previousBlockOutline = blockOutlineEnabled;

        client.options.hudHidden = true;
        renderHand = false;
        blockOutlineEnabled = false;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lazybuilder$afterRenderedFrame(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;

        if (lazybuilder$cleanCaptureActive) {
            CaptureManager.captureCleanScreenshot(client);
            client.options.hudHidden = lazybuilder$previousHudHidden;
            renderHand = lazybuilder$previousRenderHand;
            blockOutlineEnabled = lazybuilder$previousBlockOutline;
            lazybuilder$cleanCaptureActive = false;
        }

        CaptureManager.onFrameRendered(client.getFramebuffer(), System.nanoTime());
    }
}
