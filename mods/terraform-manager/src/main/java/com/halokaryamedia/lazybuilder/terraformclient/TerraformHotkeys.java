package com.halokaryamedia.lazybuilder.terraformclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

final class TerraformHotkeys {
    private TerraformHotkeys(){}
    static void register(){
        KeyBinding toggle=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.toggle",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_GRAVE_ACCENT,"key.categories.lazybuilder"));
        KeyBinding palette=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.palette",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_P,"key.categories.lazybuilder"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(toggle.wasPressed()){
                TerraformEditorState state=TerraformManagerClient.state();
                state.toggleEditor();
                TerraformInteractionController.cancelStroke();
                if(!state.editorOpen()&&client.currentScreen instanceof TerraformPaletteScreen)client.setScreen(null);
            }
            while(palette.wasPressed()){
                if(!TerraformManagerClient.state().editorOpen())continue;
                if(client.currentScreen instanceof TerraformPaletteScreen)client.setScreen(null);
                else if(client.currentScreen==null)client.setScreen(new TerraformPaletteScreen());
            }
        });
    }
}
