package com.halokaryamedia.lazybuilder.utility.screenshot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Builds optional contextual screenshot names while leaving vanilla F2 capture intact. */
public final class ScreenshotNaming {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");
    private static final AtomicLong LAST_TIMESTAMP_MILLIS = new AtomicLong(Long.MIN_VALUE);

    private ScreenshotNaming() {
    }

    public static String contextualFileName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String context = resolveContext(client);
        long timestampMillis = uniqueTimestampMillis();
        String timestamp = TIMESTAMP.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()));
        return sanitize(context) + "_" + timestamp + ".png";
    }

    private static long uniqueTimestampMillis() {
        long now = System.currentTimeMillis();
        return LAST_TIMESTAMP_MILLIS.updateAndGet(previous -> Math.max(now, previous + 1));
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
