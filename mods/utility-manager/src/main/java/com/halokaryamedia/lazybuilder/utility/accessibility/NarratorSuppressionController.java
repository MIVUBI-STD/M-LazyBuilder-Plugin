package com.halokaryamedia.lazybuilder.utility.accessibility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.NarratorMode;

/**
 * Applies the Utility narrator preference through Minecraft's own GameOptions.
 * Accessibility infrastructure remains intact; only the active narrator mode and
 * narrator hotkey are disabled when the user opts into suppression.
 */
public final class NarratorSuppressionController {
    private NarratorSuppressionController() {
    }

    public static void applyIfEnabled(MinecraftClient client, boolean enabled) {
        if (!enabled || client == null || client.options == null) return;

        client.options.getNarrator().setValue(NarratorMode.OFF);
        client.options.getNarratorHotkey().setValue(false);
    }
}
