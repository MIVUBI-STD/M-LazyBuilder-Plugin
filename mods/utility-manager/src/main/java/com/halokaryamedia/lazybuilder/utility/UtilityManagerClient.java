package com.halokaryamedia.lazybuilder.utility;

import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.reload.ResourceReloadNotifier;
import com.halokaryamedia.lazybuilder.utility.window.BorderlessWindowController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Fabric client entrypoint for LazyBuilder Utility Manager. */
public final class UtilityManagerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Utility");
    private static UtilityConfigStore configStore;
    private static UtilityPreferences preferences = UtilityPreferences.defaults();

    @Override
    public void onInitializeClient() {
        configStore = new UtilityConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();
        LOGGER.info(
                "Utility Manager loaded; reconnect={}, keepDraft={}, extendedHistory={}, borderless={}, contextualScreenshots={}",
                preferences.reconnectButton(),
                preferences.keepChatDraft(),
                preferences.extendedChatHistory(),
                preferences.borderlessWindow(),
                preferences.contextualScreenshotNames()
        );

        ResourceReloadNotifier.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourceReloadNotifier.markClientStarted();
            BorderlessWindowController.applyIfEnabled(client, preferences.borderlessWindow());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.debug("Client JOIN event received; refreshing reconnect target");
            ReconnectState.capture(client.getCurrentServerEntry());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.debug("Client DISCONNECT event received");
            ChatDraftState.clear();
        });
    }

    public static UtilityPreferences preferences() {
        return preferences;
    }

    public static void updatePreferences(UtilityPreferences updated) {
        preferences = Objects.requireNonNull(updated, "updated");
        if (configStore != null) configStore.save(updated);
    }

    public static UtilityConfigStore configStore() {
        return configStore;
    }
}
