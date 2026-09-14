package com.halokaryamedia.lazybuilder.utility.notification;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Single notification surface for Utility Manager features.
 *
 * Uses Minecraft's native toast UI so individual utility features do not create
 * competing notification systems or permanent HUD elements.
 */
public final class UtilityNotifications {
    private static final SystemToast.Type UTILITY_TOAST = new SystemToast.Type();

    private UtilityNotifications() {
    }

    public static void show(String title, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> SystemToast.show(
                client.getToastManager(),
                UTILITY_TOAST,
                Text.literal(title),
                Text.literal(message)
        ));
    }
}
