package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.screenshot.ScreenshotNaming;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds contextual names to normal screenshots without replacing vanilla F2 capture. */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotRecorderMixin {
    @ModifyVariable(
            method = "saveScreenshotInner",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static String lazybuilder$contextualScreenshotName(String fileName) {
        if (!UtilityManagerClient.preferences().contextualScreenshotNames()) return fileName;
        if (fileName != null && !fileName.isBlank()) return fileName;
        return ScreenshotNaming.contextualFileName();
    }
}
