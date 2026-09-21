package com.halokaryamedia.lazybuilder.utility;

import com.halokaryamedia.lazybuilder.utility.accessibility.NarratorSuppressionController;
import com.halokaryamedia.lazybuilder.utility.chat.ChatCollapseState;
import com.halokaryamedia.lazybuilder.utility.chat.ChatDraftState;
import com.halokaryamedia.lazybuilder.utility.chat.ChatSearchHistory;
import com.halokaryamedia.lazybuilder.utility.chat.ChatSessionPresentation;
import com.halokaryamedia.lazybuilder.utility.chat.MinecraftMessageBridge;
import com.halokaryamedia.lazybuilder.utility.chat.UtilityMessageBus;
import com.halokaryamedia.lazybuilder.utility.chat.UtilityMessageDispatcher;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.capture.CaptureManager;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugInteraction;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugNetworking;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugServerState;
import com.halokaryamedia.lazybuilder.utility.reload.ResourceReloadNotifier;
import com.halokaryamedia.lazybuilder.utility.ui.PauseMenuController;
import com.halokaryamedia.lazybuilder.utility.window.BorderlessWindowController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.Objects;

/** Fabric client entrypoint for LazyBuilder Utility Manager. */
public final class UtilityManagerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Utility");
    private static final UtilityMessageBus MESSAGE_BUS = new UtilityMessageBus();
    private static final ChatSearchHistory CHAT_SEARCH_HISTORY = new ChatSearchHistory();
    private static final ChatCollapseState CHAT_COLLAPSE_STATE = new ChatCollapseState();
    private static UtilityConfigStore configStore;
    private static UtilityPreferences preferences = UtilityPreferences.defaults();
    private static KeyBinding toggleRecordingKey;

    @Override
    public void onInitializeClient() {
        configStore = new UtilityConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();
        CaptureManager.initialize(FabricLoader.getInstance().getConfigDir());
        LOGGER.info(
                "Utility Manager loaded; reconnect={}, keepDraft={}, extendedHistory={}, chatSearch={}, chatTimestamps={}, hideSigningIndicators={}, hideReportButton={}, suppressNarrator={}, borderless={}, contextualScreenshots={}, instantCreativeSearch={}, compactDebug={}",
                preferences.reconnectButton(),
                preferences.keepChatDraft(),
                preferences.extendedChatHistory(),
                preferences.chatSearch(),
                preferences.chatTimestamps(),
                preferences.hideChatSigningIndicators(),
                preferences.hideChatReportButton(),
                preferences.suppressNarrator(),
                preferences.borderlessWindow(),
                preferences.contextualScreenshotNames(),
                preferences.instantCreativeSearch(),
                preferences.compactDebugHud()
        );

        ResourceReloadNotifier.register();
        PauseMenuController.register();
        CompactDebugNetworking.register();
        MinecraftMessageBridge.register();

        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lazybuilder.capture.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "key.categories.lazybuilder"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleRecordingKey == null) return;
            while (toggleRecordingKey.wasPressed()) CaptureManager.toggleVideo(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CaptureManager.shutdown());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourceReloadNotifier.markClientStarted();
            BorderlessWindowController.applyIfEnabled(client, preferences.borderlessWindow());
            NarratorSuppressionController.applyIfEnabled(client, preferences.suppressNarrator());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            LOGGER.debug("Client JOIN event received; refreshing reconnect target and compact telemetry");
            MESSAGE_BUS.clearSession();
            CHAT_SEARCH_HISTORY.clearSession();
            CHAT_COLLAPSE_STATE.clear();
            CompactDebugInteraction.invalidate(client);
            CompactDebugServerState.clear();
            ReconnectState.capture(client.getCurrentServerEntry());

            String target = ReconnectState.serverAddress();
            if (target.isBlank()) target = "Local World";
            UtilityMessageDispatcher.publish(ChatSessionPresentation.started(
                    target,
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
            ));

            if (preferences.compactDebugHud()) CompactDebugNetworking.requestSnapshot();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            LOGGER.debug("Client DISCONNECT event received");
            MESSAGE_BUS.clearSession();
            CHAT_SEARCH_HISTORY.clearSession();
            CHAT_COLLAPSE_STATE.clear();
            ChatDraftState.clear();
            CompactDebugInteraction.invalidate(client);
            CompactDebugServerState.clear();
        }));
    }

    public static UtilityPreferences preferences() {
        return preferences;
    }

    public static UtilityMessageBus messageBus() {
        return MESSAGE_BUS;
    }

    public static ChatSearchHistory chatSearchHistory() {
        return CHAT_SEARCH_HISTORY;
    }

    public static ChatCollapseState chatCollapseState() {
        return CHAT_COLLAPSE_STATE;
    }

    public static void updatePreferences(UtilityPreferences updated) {
        Objects.requireNonNull(updated, "updated");
        boolean compactDebugDisabled = preferences.compactDebugHud() && !updated.compactDebugHud();
        boolean compactDebugEnabled = !preferences.compactDebugHud() && updated.compactDebugHud();
        boolean narratorSuppressionEnabled = !preferences.suppressNarrator() && updated.suppressNarrator();
        boolean chatSearchDisabled = preferences.chatSearch() && !updated.chatSearch();
        boolean keepChatDraftDisabled = preferences.keepChatDraft() && !updated.keepChatDraft();
        preferences = updated;
        if (configStore != null) configStore.save(updated);

        if (chatSearchDisabled) CHAT_SEARCH_HISTORY.clearSession();
        if (keepChatDraftDisabled) ChatDraftState.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        if (compactDebugDisabled) {
            client.execute(() -> {
                CompactDebugInteraction.invalidate(client);
                CompactDebugServerState.clear();
            });
        } else if (compactDebugEnabled) {
            client.execute(CompactDebugNetworking::requestSnapshot);
        }
        if (narratorSuppressionEnabled) {
            client.execute(() -> NarratorSuppressionController.applyIfEnabled(client, true));
        }
    }

    public static UtilityConfigStore configStore() {
        return configStore;
    }
}
