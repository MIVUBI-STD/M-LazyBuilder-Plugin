package com.halokaryamedia.lazybuilder.utility.connection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Session-only reconnect target. No server address is persisted to disk. */
public final class ReconnectState {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Utility/Reconnect");
    private static ServerInfo lastServer;

    private ReconnectState() {
    }

    public static void capture(ServerInfo serverInfo) {
        if (serverInfo != null && serverInfo.address != null && !serverInfo.address.isBlank()) {
            lastServer = serverInfo;
            LOGGER.debug("Captured reconnect target for the current client session");
            return;
        }

        clear();
        LOGGER.debug("Cleared reconnect target because the current client context has no multiplayer target");
    }

    public static void clear() {
        lastServer = null;
    }

    public static boolean canReconnect() {
        return lastServer != null && lastServer.address != null && !lastServer.address.isBlank();
    }

    public static String serverAddress() {
        return canReconnect() ? lastServer.address : "";
    }

    public static void reconnect(Screen parent) {
        if (!canReconnect()) {
            LOGGER.warn("Reconnect requested without a captured server target");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        LOGGER.debug("Reconnect requested for the captured client-session target");
        ConnectScreen.connect(
                parent,
                client,
                ServerAddress.parse(lastServer.address),
                lastServer,
                false,
                null
        );
    }
}
