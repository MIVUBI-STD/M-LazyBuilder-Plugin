package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Registers the map-first LazyBuilder client entry point. */
public final class LazyBuilderClientUi {
    private static boolean mapWasOpenLastTick;

    private LazyBuilderClientUi() {}

    public static void register(ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
        KeyBinding openMap = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.open_world_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.category.lazybuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean mapOpenAtTickStart = client.currentScreen instanceof WorldMapScreen;
            while (openMap.wasPressed()) {
                if (client.player == null) continue;
                if (client.currentScreen instanceof WorldMapScreen mapScreen) {
                    mapScreen.closeFromToggle();
                    continue;
                }
                // If the Screen handled M earlier in this same tick, do not consume the
                // matching keybinding event as a new open request (close -> reopen race).
                if (mapWasOpenLastTick && !mapOpenAtTickStart && client.currentScreen == null) continue;
                // Do not replace another active screen. M is a map toggle from normal gameplay,
                // not a global screen override that can discard another workflow's UI state.
                if (client.currentScreen != null) continue;
                if (!controller.worldListReady() && !controller.worldListPending()) {
                    controller.refresh();
                }
                client.setScreen(new WorldMapScreen(controller, transfers, maps));
            }
            mapWasOpenLastTick = client.currentScreen instanceof WorldMapScreen;
        });
    }
}
