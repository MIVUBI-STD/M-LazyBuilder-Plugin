package com.halokaryamedia.lazybuilder.utility;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint for LazyBuilder Utility Manager.
 *
 * The scaffold intentionally registers no keybinds, pollers, watchers, or
 * building tools. Features are added only inside the approved passive
 * client-convenience boundary.
 */
public final class UtilityManagerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // C2 scaffold only. Keep initialization side-local and event-driven.
    }
}
