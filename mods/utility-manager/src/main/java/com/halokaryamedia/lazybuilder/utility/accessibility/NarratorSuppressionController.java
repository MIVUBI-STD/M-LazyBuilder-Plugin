package com.halokaryamedia.lazybuilder.utility.accessibility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.NarratorMode;

/**
 * A reversible, session-local preference applied through Minecraft GameOptions.
 * Does not replace narrator infrastructure or write a second preferences file.
 */
public final class NarratorSuppressionController {
    private static NarratorMode previousMode;
    private static Boolean previousHotkey;
    private static boolean ownsSuppression;

    private NarratorSuppressionController() { }

    /**
     * Turning this preference off restores the prior vanilla values only if
     * they still have the values applied by LazyBuilder. User/third-party
     * changes made meanwhile are authoritative.
     */
    public static void applyIfEnabled(MinecraftClient client, boolean enabled) {
        if (client == null || client.options == null) return;

        if (enabled) {
            if (!ownsSuppression) {
                previousMode = client.options.getNarrator().getValue();
                previousHotkey = client.options.getNarratorHotkey().getValue();
                ownsSuppression = true;
            }
            client.options.getNarrator().setValue(NarratorMode.OFF);
            client.options.getNarratorHotkey().setValue(false);
            return;
        }

        if (!ownsSuppression) return;
        ownsSuppression = false;
        NarratorMode restoreMode = previousMode;
        Boolean restoreHotkey = previousHotkey;
        previousMode = null;
        previousHotkey = null;

        if (client.options.getNarrator().getValue() == NarratorMode.OFF && restoreMode != null) {
            client.options.getNarrator().setValue(restoreMode);
        }
        if (!client.options.getNarratorHotkey().getValue() && restoreHotkey != null) {
            client.options.getNarratorHotkey().setValue(restoreHotkey);
        }
    }
}
