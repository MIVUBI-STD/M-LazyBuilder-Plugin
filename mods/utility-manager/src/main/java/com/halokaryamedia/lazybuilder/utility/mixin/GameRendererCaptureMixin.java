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
        if (client == null) return;

        // If the previous render threw before its RETURN hook, restore the
        // temporary clean-capture view at the next render entry. Do not keep
        // the player's HUD hidden after a recoverable frame exception.
        if (lazybuilder$cleanCaptureActive) {
            client.options.hudHidden = lazybuilder$previousHudHidden;
            renderHand = lazybuilder$previousRenderHand;
            blockOutlineEnabled = lazybuilder$previousBlockOutline;
            lazybuilder$cleanCaptureActive = false;
        }
        if (!CaptureManager.consumeCleanScreenshotRequest()) return;

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
        if (client == null) return;

        if (lazybuilder$cleanCaptureActive) {
            try {
                if (client.getFramebuffer() != null) {
                    CaptureManager.captureCleanScreenshot(client);
                }
            } finally {
                // The temporary clean-capture view must never survive a
                // screenshot encoder/readback failure or missing framebuffer.
                client.options.hudHidden = lazybuilder$previousHudHidden;
                renderHand = lazybuilder$previousRenderHand;
                blockOutlineEnabled = lazybuilder$previousBlockOutline;
                lazybuilder$cleanCaptureActive = false;
            }
        }

        if (client.getFramebuffer() != null) {
            CaptureManager.onFrameRendered(client.getFramebuffer(), System.nanoTime());
        }
    }
}
