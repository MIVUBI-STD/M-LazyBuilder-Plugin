package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugInteraction;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporarily releases the pointer while Alt is held over an active Compact Debug HUD. */
@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void lazybuilder$compactDebugInteraction(
            long window,
            int key,
            int scancode,
            int action,
            int modifiers,
            CallbackInfo ci
    ) {
        if (!UtilityManagerClient.preferences().compactDebugHud()) return;
        if (window != client.getWindow().getHandle()) return;
        if (key != GLFW.GLFW_KEY_LEFT_ALT && key != GLFW.GLFW_KEY_RIGHT_ALT) return;

        if (action == GLFW.GLFW_PRESS) {
            CompactDebugInteraction.begin(client);
        } else if (action == GLFW.GLFW_RELEASE) {
            CompactDebugInteraction.end(client);
        }
    }
}
