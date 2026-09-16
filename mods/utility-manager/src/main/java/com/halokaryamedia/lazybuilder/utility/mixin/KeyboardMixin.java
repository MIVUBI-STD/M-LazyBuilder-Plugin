package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugRenderer;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the familiar F3+C chord to copy the clean XYZ triplet while Compact Debug is active. */
@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$copyCompactCoordinates(
            long window,
            int key,
            int scancode,
            int action,
            int modifiers,
            CallbackInfo ci
    ) {
        if (!UtilityManagerClient.preferences().compactDebugHud()) return;
        if (action != GLFW.GLFW_PRESS || key != GLFW.GLFW_KEY_C) return;
        if (client.player == null || client.currentScreen != null) return;
        if (window != client.getWindow().getHandle()) return;
        if (!InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_F3)) return;

        CompactDebugRenderer.copyCoordinates(client);
        ci.cancel();
    }
}
