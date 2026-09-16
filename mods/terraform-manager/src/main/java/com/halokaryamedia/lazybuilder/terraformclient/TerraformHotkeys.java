package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class TerraformHotkeys {
    private TerraformHotkeys(){}
    static void register(){
        KeyBinding toggle=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.toggle",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_GRAVE_ACCENT,"key.categories.lazybuilder"));
        KeyBinding palette=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.palette",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_P,"key.categories.lazybuilder"));
        KeyBinding previousTool=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.previous_tool",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_BRACKET,"key.categories.lazybuilder"));
        KeyBinding nextTool=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.next_tool",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_BRACKET,"key.categories.lazybuilder"));
        KeyBinding variation=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.variation",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.lazybuilder"));
        KeyBinding undo=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lazybuilder.terraform.undo",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Z,"key.categories.lazybuilder"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(toggle.wasPressed()){
                TerraformEditorState state=TerraformManagerClient.state();
                state.toggleEditor();
                TerraformInteractionController.cancelStroke();
                if(!state.editorOpen()&&client.currentScreen instanceof TerraformPaletteScreen)client.setScreen(null);
                if(state.editorOpen()&&client.player!=null)client.player.sendMessage(Text.literal("Terraform enabled · [ / ] tool · V variation · P options"),true);
            }
            while(palette.wasPressed()){
                if(!TerraformManagerClient.state().editorOpen())continue;
                if(client.currentScreen instanceof TerraformPaletteScreen)client.setScreen(null);
                else if(client.currentScreen==null)client.setScreen(new TerraformPaletteScreen());
            }
            if(!TerraformManagerClient.state().editorOpen()||client.currentScreen!=null)return;
            while(previousTool.wasPressed())cycleTool(client,-1);
            while(nextTool.wasPressed())cycleTool(client,1);
            while(variation.wasPressed())cycleVariation(client);
            while(undo.wasPressed())if(Screen.hasControlDown())TerraformInteractionController.undo();
        });
    }
    private static void cycleTool(net.minecraft.client.MinecraftClient client,int delta){
        TerraformInteractionController.cancelStroke();
        TerraformEditorState state=TerraformManagerClient.state();
        TerrainTool[] values=TerrainTool.values();
        int index=Math.floorMod(state.tool().ordinal()+delta,values.length);
        state.setTool(values[index]);
        status(client,state);
    }
    private static void cycleVariation(net.minecraft.client.MinecraftClient client){
        TerraformInteractionController.cancelStroke();
        TerraformEditorState state=TerraformManagerClient.state();
        TerrainVariation[] values=TerrainVariation.values();
        state.setVariation(values[(state.variation().ordinal()+1)%values.length]);
        status(client,state);
    }
    private static void status(net.minecraft.client.MinecraftClient client,TerraformEditorState state){
        if(client.player==null)return;
        client.player.sendMessage(Text.literal(pretty(state.tool().name())+" · Size "+(int)state.size()+" · Height "+(int)state.height()+" · "+pretty(state.variation().name())),true);
    }
    private static String pretty(String value){String v=value.toLowerCase();return Character.toUpperCase(v.charAt(0))+v.substring(1);}
}
