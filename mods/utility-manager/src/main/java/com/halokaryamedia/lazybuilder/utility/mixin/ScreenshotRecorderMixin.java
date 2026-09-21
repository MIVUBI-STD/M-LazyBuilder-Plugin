package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.capture.CaptureManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * Routes normal F2 captures through LazyBuilder's bounded encoder while preserving vanilla as the
 * correctness fallback whenever the first-party capture path cannot accept the request.
 */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotRecorderMixin {
    @Inject(method = "saveScreenshotInner", at = @At("HEAD"), cancellable = true)
    private static void lazybuilder$captureScreenshot(
            File gameDirectory,
            String fileName,
            Framebuffer framebuffer,
            Consumer<Text> messageReceiver,
            CallbackInfo ci
    ) {
        if (CaptureManager.captureScreenshot(
                gameDirectory,
                fileName,
                framebuffer,
                messageReceiver
        )) {
            ci.cancel();
        }
    }
}
