package com.halokaryamedia.lazybuilder.utility.mixin;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugInteraction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes only Compact Debug pointer clicks while its temporary Alt interaction is active. */
@Mixin(Mouse.class)
abstract class CompactDebugMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$compactDebugClick(
            long window,
            int button,
            int action,
            int mods,
            CallbackInfo ci
    ) {
        if (!UtilityManagerClient.preferences().compactDebugHud()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || window != client.getWindow().getHandle()) return;
        if (!CompactDebugInteraction.interactionActive()) return;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        if (action == GLFW.GLFW_PRESS) {
            CompactDebugInteraction.copyIfCoordinateHit(client);
        }

        // While Alt interaction owns the pointer, do not also dispatch a gameplay left click.
        ci.cancel();
    }
}
