package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Registers the lightweight first-party World Manager entry point. */
public final class WorldManagerClientUi {
    private WorldManagerClientUi() {}

    public static void register(ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.open_world_manager",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.category.lazybuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed()) {
                if (client.player == null) continue;
                controller.refresh();
                client.setScreen(new WorldManagerScreen(controller, transfers, maps));
            }
        });
    }
}
