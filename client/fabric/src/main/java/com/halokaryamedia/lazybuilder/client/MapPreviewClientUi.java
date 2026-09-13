package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Owns the standalone LazyBuilder map-preview entry point. */
public final class MapPreviewClientUi {
    private MapPreviewClientUi() {}

    public static void register(ClientMapController maps) {
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.open_map_preview", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M,
                "key.category.lazybuilder"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed() && client.player != null) client.setScreen(new MapPreviewScreen(maps));
        });
    }
}
