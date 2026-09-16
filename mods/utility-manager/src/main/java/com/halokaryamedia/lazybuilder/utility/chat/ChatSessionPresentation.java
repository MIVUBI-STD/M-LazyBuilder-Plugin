package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Builds the single compact separator shown when a new play session starts. */
public final class ChatSessionPresentation {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ChatSessionPresentation() {
    }

    public static ChatMessage started(String targetName, long epochMillis, ZoneId zoneId) {
        String target = targetName == null || targetName.isBlank() ? "Minecraft" : targetName.trim();
        String time = TIME.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis));
        return new ChatMessage(
                ChatMessageType.SYSTEM,
                "",
                "──────── " + target + " • " + time + " ────────",
                ""
        );
    }
}
