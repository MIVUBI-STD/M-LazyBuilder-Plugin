package com.halokaryamedia.lazybuilder.terraformclient.mixin;

import com.halokaryamedia.lazybuilder.terraformclient.TerraformInteractionController;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Narrow input hook used only because Fabric has no global scroll/button callback for the live viewport. */
@Mixin(Mouse.class)
abstract class TerraformMouseMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void lazybuilder$terraformButton(long window,int button,int action,int mods,CallbackInfo ci){
        if(!TerraformInteractionController.active())return;
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT){if(action==GLFW.GLFW_PRESS)TerraformInteractionController.beginStroke();else if(action==GLFW.GLFW_RELEASE)TerraformInteractionController.endStroke();ci.cancel();}
        else if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT&&action==GLFW.GLFW_PRESS){TerraformInteractionController.flipFace();ci.cancel();}
    }
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void lazybuilder$terraformScroll(long window,double horizontal,double vertical,CallbackInfo ci){
        if(!TerraformInteractionController.active())return;TerraformInteractionController.adjustWheel(vertical,Screen.hasShiftDown());ci.cancel();
    }
}
