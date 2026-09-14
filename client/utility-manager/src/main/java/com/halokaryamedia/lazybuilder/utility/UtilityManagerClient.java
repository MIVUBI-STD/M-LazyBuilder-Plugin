package com.halokaryamedia.lazybuilder.utility;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint for LazyBuilder Utility Manager. */
public final class UtilityManagerClient implements ClientModInitializer {
    private static UtilityConfigStore configStore;
    private static UtilityPreferences preferences = UtilityPreferences.defaults();

    @Override
    public void onInitializeClient() {
        configStore = new UtilityConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();
        // C2 foundation only: loading preferences does not apply feature behavior yet.
    }

    public static UtilityPreferences preferences() {
        return preferences;
    }

    public static void updatePreferences(UtilityPreferences updated) {
        preferences = updated;
        if (configStore != null) configStore.save(updated);
    }

    public static UtilityConfigStore configStore() {
        return configStore;
    }
}
