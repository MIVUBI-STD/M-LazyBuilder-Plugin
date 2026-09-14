package com.halokaryamedia.lazybuilder.utility.connection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Session-only reconnect target. No server address is persisted to disk. */
public final class ReconnectState {
    private static ServerInfo lastServer;

    private ReconnectState() {
    }

    public static void capture(ServerInfo serverInfo) {
        if (serverInfo != null && serverInfo.address != null && !serverInfo.address.isBlank()) {
            lastServer = serverInfo;
        }
    }

    public static boolean canReconnect() {
        return lastServer != null && lastServer.address != null && !lastServer.address.isBlank();
    }

    public static void reconnect(Screen parent) {
        if (!canReconnect()) return;

        MinecraftClient client = MinecraftClient.getInstance();
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
