package com.halokaryamedia.lazybuilder.utility.clipboard;

import com.halokaryamedia.lazybuilder.utility.notification.UtilityNotifications;
import net.minecraft.client.MinecraftClient;

/** Small contextual clipboard helper; no keybinds, history, or build-data ownership. */
public final class UtilityClipboard {
    private UtilityClipboard() {
    }

    public static void copy(String text, String confirmation) {
        if (text == null || text.isBlank()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.keyboard == null) return;

        client.keyboard.setClipboard(text);
        UtilityNotifications.show("Copied", confirmation);
    }
}
