package com.halokaryamedia.lazybuilder.utility;

import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.window.BorderlessWindowController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint for LazyBuilder Utility Manager. */
public final class UtilityManagerClient implements ClientModInitializer {
    private static UtilityConfigStore configStore;
    private static UtilityPreferences preferences = UtilityPreferences.defaults();

    @Override
    public void onInitializeClient() {
        configStore = new UtilityConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                BorderlessWindowController.applyIfEnabled(client, preferences.borderlessWindow())
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ReconnectState.capture(client.getCurrentServerEntry())
        );
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
