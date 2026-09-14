package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Registers the map-first LazyBuilder client entry point. */
public final class LazyBuilderClientUi {
    private LazyBuilderClientUi() {}

    public static void register(ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
        KeyBinding openMap = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.open_world_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.category.lazybuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMap.wasPressed()) {
                if (client.player == null) continue;
                // Map actions such as Export Area depend on authoritative server capabilities.
                // Prime them on the map-first entry so users do not have to open Worlds once
                // merely to make a permission-gated map action available.
                if (!controller.worldListReady() && !controller.worldListPending()) {
                    controller.refresh();
                }
                client.setScreen(new WorldMapScreen(controller, transfers, maps));
            }
        });
    }
}
