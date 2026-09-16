package com.halokaryamedia.lazybuilder.utility;

import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import com.halokaryamedia.lazybuilder.utility.chat.UtilityMessageBus;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugNetworking;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugServerState;
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
    private static final UtilityMessageBus MESSAGE_BUS = new UtilityMessageBus();
    private static UtilityConfigStore configStore;
    private static UtilityPreferences preferences = UtilityPreferences.defaults();

    @Override
    public void onInitializeClient() {
        configStore = new UtilityConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();
        LOGGER.info(
                "Utility Manager loaded; reconnect={}, keepDraft={}, extendedHistory={}, borderless={}, contextualScreenshots={}, instantCreativeSearch={}, compactDebug={}",
                preferences.reconnectButton(),
                preferences.keepChatDraft(),
                preferences.extendedChatHistory(),
                preferences.borderlessWindow(),
                preferences.contextualScreenshotNames(),
                preferences.instantCreativeSearch(),
                preferences.compactDebugHud()
        );

        ResourceReloadNotifier.register();
        CompactDebugNetworking.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourceReloadNotifier.markClientStarted();
            BorderlessWindowController.applyIfEnabled(client, preferences.borderlessWindow());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            LOGGER.debug("Client JOIN event received; refreshing reconnect target and compact telemetry");
            MESSAGE_BUS.clearSession();
            CompactDebugServerState.clear();
            ReconnectState.capture(client.getCurrentServerEntry());
            if (preferences.compactDebugHud()) CompactDebugNetworking.requestSnapshot();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            LOGGER.debug("Client DISCONNECT event received");
            MESSAGE_BUS.clearSession();
            ChatDraftState.clear();
            CompactDebugServerState.clear();
        }));
    }

    public static UtilityPreferences preferences() {
        return preferences;
    }

    public static UtilityMessageBus messageBus() {
        return MESSAGE_BUS;
    }

    public static void updatePreferences(UtilityPreferences updated) {
        preferences = Objects.requireNonNull(updated, "updated");
        if (configStore != null) configStore.save(updated);
    }

    public static UtilityConfigStore configStore() {
        return configStore;
    }
}
