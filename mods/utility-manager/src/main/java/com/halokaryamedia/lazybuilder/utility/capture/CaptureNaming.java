package com.halokaryamedia.lazybuilder.utility.capture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Shared deterministic naming for screenshot and future video captures. */
public final class CaptureNaming {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");
    private static final AtomicLong LAST_TIMESTAMP_MILLIS = new AtomicLong(Long.MIN_VALUE);

    private CaptureNaming() {}

    public static String screenshotFileName(boolean contextual, CapturePreferences.ScreenshotQuality quality) {
        String prefix = contextual ? resolveContext(MinecraftClient.getInstance()) : "minecraft";
        return sanitize(prefix) + "_" + timestamp() + quality.extension();
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

    private static String timestamp() {
        long now = System.currentTimeMillis();
        long unique = LAST_TIMESTAMP_MILLIS.updateAndGet(previous -> Math.max(now, previous + 1));
        return TIMESTAMP.format(Instant.ofEpochMilli(unique).atZone(ZoneId.systemDefault()));
    }

    private static String resolveContext(MinecraftClient client) {
        if (client == null) return "minecraft";
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            if (server.name != null && !server.name.isBlank()) return server.name;
            if (server.address != null && !server.address.isBlank()) return server.address;
        }
        if (client.isInSingleplayer()) return "singleplayer";
        return "minecraft";
    }
}
