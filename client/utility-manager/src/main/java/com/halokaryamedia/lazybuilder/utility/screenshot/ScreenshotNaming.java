package com.halokaryamedia.lazybuilder.utility.screenshot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Util;

import java.util.Locale;

/** Builds optional contextual screenshot names while leaving vanilla F2 capture intact. */
public final class ScreenshotNaming {
    private ScreenshotNaming() {
    }

    public static String contextualFileName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String context = resolveContext(client);
        return sanitize(context) + "_" + Util.getFormattedCurrentTime() + ".png";
    }

    private static String resolveContext(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            if (server.name != null && !server.name.isBlank()) return server.name;
            if (server.address != null && !server.address.isBlank()) return server.address;
        }

        if (client.isInSingleplayer()) return "singleplayer";
        return "minecraft";
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) return "minecraft";

        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[._-]+|[._-]+$", "");

        if (normalized.isBlank()) return "minecraft";
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }
}
