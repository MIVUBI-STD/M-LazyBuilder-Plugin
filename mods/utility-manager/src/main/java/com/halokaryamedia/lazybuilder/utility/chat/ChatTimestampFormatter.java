package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Compact HH:mm timestamp formatter for rendered chat lines. */
public final class ChatTimestampFormatter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private ChatTimestampFormatter() {
    }

    public static String format(long timestampMillis, ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return FORMATTER.format(Instant.ofEpochMilli(timestampMillis).atZone(zoneId));
    }
}
