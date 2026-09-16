package com.halokaryamedia.lazybuilder.terraformclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Single keyboard entry point for the Terraform editor, matching Axiom's default editor toggle. */
final class TerraformHotkeys {
    private TerraformHotkeys() {}

    static void register() {
        KeyBinding togglePanel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.terraform.toggle_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.lazybuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (togglePanel.wasPressed()) {
                TerraformEditorState state = TerraformManagerClient.state();
                if (!state.editorOpen()) {
                    state.setEditorOpen(true);
                    TerraformInteractionController.cancelStroke();
                    if (client.currentScreen == null) client.setScreen(new TerraformPaletteScreen());
                } else if (client.currentScreen instanceof TerraformPaletteScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    TerraformInteractionController.cancelStroke();
                    client.setScreen(new TerraformPaletteScreen());
                }
            }
        });
    }
}
