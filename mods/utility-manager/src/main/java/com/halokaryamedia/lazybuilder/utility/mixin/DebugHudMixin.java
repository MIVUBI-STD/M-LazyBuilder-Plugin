package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the rendered F3 information surface when Compact Debug is enabled. */
@Mixin(DebugHud.class)
abstract class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$renderCompactDebug(DrawContext context, CallbackInfo ci) {
        if (!UtilityManagerClient.preferences().compactDebugHud()) return;

        CompactDebugRenderer.render(MinecraftClient.getInstance(), context);
        ci.cancel();
    }
}
